package dj.redseabunkering.crm.sequence;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.contact.Contact;
import dj.redseabunkering.crm.contact.ContactRepository;
import dj.redseabunkering.crm.email.EmailService;
import dj.redseabunkering.crm.email.EmailService.Delivery;
import dj.redseabunkering.crm.task.Task;
import dj.redseabunkering.crm.task.TaskPriority;
import dj.redseabunkering.crm.task.TaskRepository;

/**
 * Séquences (règle 12) : l'inscription d'un contact crée une tâche par étape pour son propriétaire. Si l'envoi
 * automatique est activé sur la séquence, un job envoie les étapes e-mail arrivées à échéance et clôt leur tâche.
 * Chaque étape n'est tentée qu'une fois : après un échec, la tâche reste ouverte pour un envoi manuel.
 */
@Service
public class SequenceService {

    private static final Logger log = LoggerFactory.getLogger(SequenceService.class);

    private static final String DUE_STEPS = """
            select e.id as enrollment_id, e.contact_id, s.id as step_id, s.email_template_id, c.owner_id
            from sequence_enrollments e
            join sequences q on q.id = e.sequence_id and q.auto_send and q.active
            join sequence_steps s on s.sequence_id = q.id and s.type = 'EMAIL' and s.email_template_id is not null
            join contacts c on c.id = e.contact_id
            where e.status = 'ACTIVE'
              and (e.enrolled_at at time zone 'Africa/Djibouti')::date + s.day_offset <= :today
              and not exists (select 1 from sequence_step_runs r where r.enrollment_id = e.id and r.step_id = s.id)
            order by e.enrolled_at, s.day_offset""";

    /** Une inscription se termine une fois sa dernière étape passée. */
    private static final String FINISH = """
            update sequence_enrollments e set status = 'TERMINEE', updated_at = now()
            where e.status = 'ACTIVE' and (e.enrolled_at at time zone 'Africa/Djibouti')::date
                + (select coalesce(max(s.day_offset), 0) from sequence_steps s where s.sequence_id = e.sequence_id) < :today""";

    private final SequenceRepository sequences;
    private final SequenceStepRepository steps;
    private final SequenceEnrollmentRepository enrollments;
    private final ContactRepository contacts;
    private final TaskRepository tasks;
    private final EmailService emails;
    private final BusinessCalendar calendar;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SequenceService(SequenceRepository sequences, SequenceStepRepository steps,
            SequenceEnrollmentRepository enrollments, ContactRepository contacts, TaskRepository tasks,
            EmailService emails, BusinessCalendar calendar, NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager txManager, Clock clock) {
        this.sequences = sequences;
        this.steps = steps;
        this.enrollments = enrollments;
        this.contacts = contacts;
        this.tasks = tasks;
        this.emails = emails;
        this.calendar = calendar;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SequenceDto> list() {
        return sequences.findByActiveTrueOrderByName().stream().map(s -> new SequenceDto(s.getId(), s.getName(),
                s.getDescription(), s.isAutoSend(),
                steps.findBySequenceIdOrderByDayOffset(s.getId()).stream()
                        .map(st -> new StepDto(st.getId(), st.getDayOffset(), st.getType(), st.getLabel())).toList(),
                jdbc.query("""
                        select e.id, e.contact_id, c.first_name || ' ' || c.last_name, c.owner_id
                        from sequence_enrollments e join contacts c on c.id = e.contact_id
                        where e.sequence_id = :id and e.status = 'ACTIVE' order by e.enrolled_at desc""",
                        Map.of("id", s.getId()),
                        (rs, i) -> new EnrollmentDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                                rs.getString(3), rs.getObject(4, UUID.class))))).toList();
    }

    @Transactional
    public UUID enroll(UUID sequenceId, UUID contactId, CurrentUser me) {
        Sequence sequence = sequences.findById(sequenceId)
                .orElseThrow(() -> ApiException.notFound("Séquence introuvable."));
        Contact contact = contacts.findById(contactId).orElseThrow(() -> ApiException.badRequest("Contact introuvable."));
        me.requireEdit(contact.getOwnerId());
        Long already = jdbc.queryForObject("""
                select count(*) from sequence_enrollments
                where contact_id = :c and sequence_id = :s and status = 'ACTIVE'""",
                Map.of("c", contactId, "s", sequenceId), Long.class);
        if (already != null && already > 0) {
            throw ApiException.conflict("Ce contact est déjà inscrit à cette séquence.");
        }
        var enrollment = enrollments.save(new SequenceEnrollment(contactId, sequence.getId(), clock.instant()));
        UUID owner = contact.getOwnerId() != null ? contact.getOwnerId() : me.id();
        LocalDate today = calendar.today();
        for (SequenceStep step : steps.findBySequenceIdOrderByDayOffset(sequenceId)) {
            var task = new Task(step.taskTitle() + " · " + contact.fullName(), today.plusDays(step.getDayOffset()),
                    TaskPriority.MOYENNE, owner, null, contactId, contact.getCompanyId());
            task.linkSequence(enrollment.getId(), step.getId());
            tasks.save(task);
        }
        return enrollment.getId();
    }

    /** Arrêt d'une inscription : plus d'envoi automatique ; les tâches déjà créées restent aux commerciaux. */
    @Transactional
    public void stop(UUID enrollmentId, CurrentUser me) {
        var enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> ApiException.notFound("Inscription introuvable."));
        Contact contact = contacts.findById(enrollment.getContactId()).orElseThrow();
        me.requireEdit(contact.getOwnerId());
        enrollment.stop();
    }

    @Transactional
    public void setAutoSend(UUID sequenceId, boolean autoSend) {
        sequences.findById(sequenceId).orElseThrow(() -> ApiException.notFound("Séquence introuvable."))
                .setAutoSend(autoSend);
    }

    /** Toutes les 15 minutes : chaque étape due est traitée dans sa propre transaction. */
    @Scheduled(cron = "0 */15 * * * *")
    public void runDueSteps() {
        LocalDate today = calendar.today();
        record Due(UUID enrollmentId, UUID contactId, UUID stepId, UUID templateId, UUID ownerId) {
        }
        List<Due> due = jdbc.query(DUE_STEPS, Map.of("today", today), (rs, i) -> new Due(
                rs.getObject("enrollment_id", UUID.class), rs.getObject("contact_id", UUID.class),
                rs.getObject("step_id", UUID.class), rs.getObject("email_template_id", UUID.class),
                rs.getObject("owner_id", UUID.class)));
        for (Due d : due) {
            tx.executeWithoutResult(status -> {
                // L'étape est marquée d'abord : pas de double envoi, même si un autre passage démarre en parallèle
                int claimed = jdbc.update("""
                        insert into sequence_step_runs (enrollment_id, step_id) values (:e, :s)
                        on conflict do nothing""", Map.of("e", d.enrollmentId(), "s", d.stepId()));
                if (claimed == 0) {
                    return;
                }
                try {
                    var draft = emails.draft(d.templateId(), d.contactId(), null);
                    emails.deliver(new Delivery(d.contactId(), draft.dealId(), null, draft.subject(), draft.body(),
                            d.ownerId(), null, null));
                    tasks.findByEnrollmentIdAndSequenceStepId(d.enrollmentId(), d.stepId())
                            .forEach(t -> t.setDone(true, clock.instant()));
                } catch (ApiException e) {
                    log.warn("Séquence : étape {} non envoyée pour le contact {} : {}", d.stepId(), d.contactId(),
                            e.getMessage());
                }
            });
        }
        tx.executeWithoutResult(status -> jdbc.update(FINISH, Map.of("today", today)));
    }

    public record SequenceDto(UUID id, String name, String description, boolean autoSend, List<StepDto> steps,
            List<EnrollmentDto> enrolled) {
    }

    public record StepDto(UUID id, int dayOffset, String type, String label) {
    }

    public record EnrollmentDto(UUID id, UUID contactId, String contactName, UUID ownerId) {
    }
}
