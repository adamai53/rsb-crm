package dj.redseabunkering.crm.task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.BusinessCalendar;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.contact.ContactRepository;
import dj.redseabunkering.crm.deal.DealRepository;
import dj.redseabunkering.crm.user.OwnerAssignment;

/**
 * Tâches : relances, rappels d'appel, préparation des livraisons. Un SALES ne gère que ses tâches ;
 * une tâche liée à une transaction hérite de son contact et de son entreprise.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final String SELECT = """
            select t.id, t.title, t.due_date, t.priority, t.owner_id, t.done, t.done_at, t.deal_id, d.vessel_name,
                   t.contact_id, c.first_name || ' ' || c.last_name as contact_name, t.company_id
            from tasks t
            left join deals d on d.id = t.deal_id
            left join contacts c on c.id = t.contact_id
            """;

    private final TaskRepository tasks;
    private final DealRepository deals;
    private final ContactRepository contacts;
    private final OwnerAssignment owners;
    private final NamedParameterJdbcTemplate jdbc;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public TaskController(TaskRepository tasks, DealRepository deals, ContactRepository contacts,
            OwnerAssignment owners, NamedParameterJdbcTemplate jdbc, BusinessCalendar calendar, Clock clock) {
        this.tasks = tasks;
        this.deals = deals;
        this.contacts = contacts;
        this.owners = owners;
        this.jdbc = jdbc;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Tâches ouvertes (toutes) puis les 100 dernières terminées, filtrées par responsable si demandé. */
    @GetMapping
    public List<TaskDto> list(@RequestParam(required = false) UUID ownerId) {
        var params = new MapSqlParameterSource();
        String owner = "";
        if (ownerId != null) {
            owner = " and t.owner_id = :owner";
            params.addValue("owner", ownerId);
        }
        List<TaskDto> open = jdbc.query(SELECT + " where not t.done" + owner + " order by t.due_date, t.priority",
                params, (rs, i) -> row(rs));
        List<TaskDto> done = jdbc.query(
                SELECT + " where t.done" + owner + " order by t.done_at desc nulls last limit 100",
                params, (rs, i) -> row(rs));
        return Stream.concat(open.stream(), done.stream()).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, UUID> create(@Valid @RequestBody TaskRequest req, CurrentUser me) {
        UUID contactId = req.contactId();
        UUID companyId = req.companyId();
        if (req.dealId() != null) {
            var deal = deals.findById(req.dealId())
                    .orElseThrow(() -> ApiException.badRequest("Transaction introuvable."));
            contactId = deal.getContactId();
            companyId = deal.getCompanyId();
        } else if (contactId != null) {
            var contact = contacts.findById(contactId)
                    .orElseThrow(() -> ApiException.badRequest("Contact introuvable."));
            companyId = contact.getCompanyId();
        }
        var task = new Task(req.title(), req.dueDate(), req.priority(), owners.forNewRecord(req.ownerId(), me),
                req.dealId(), contactId, companyId);
        return Map.of("id", tasks.save(task).getId());
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void update(@PathVariable UUID id, @Valid @RequestBody TaskRequest req, CurrentUser me) {
        Task task = get(id);
        me.requireEdit(task.getOwnerId());
        task.update(req.title(), req.dueDate(), req.priority());
        task.setOwnerId(owners.reassign(task.getOwnerId(), req.ownerId(), me));
    }

    @PatchMapping("/{id}/done")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void setDone(@PathVariable UUID id, @RequestBody DoneRequest req, CurrentUser me) {
        Task task = get(id);
        me.requireEdit(task.getOwnerId());
        task.setDone(req.done(), Instant.now(clock));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id, CurrentUser me) {
        Task task = get(id);
        me.requireEdit(task.getOwnerId());
        tasks.delete(task);
    }

    /** Compteur de la barre latérale : tâches de l'utilisateur en retard ou dues aujourd'hui. */
    @GetMapping("/due-count")
    public Map<String, Long> dueCount(CurrentUser me) {
        Long n = jdbc.queryForObject(
                "select count(*) from tasks where owner_id = :me and not done and due_date <= :today",
                Map.of("me", me.id(), "today", calendar.today()), Long.class);
        return Map.of("due", n == null ? 0 : n);
    }

    private Task get(UUID id) {
        return tasks.findById(id).orElseThrow(() -> ApiException.notFound("Tâche introuvable."));
    }

    private static TaskDto row(ResultSet rs) throws SQLException {
        var doneAt = rs.getTimestamp("done_at");
        return new TaskDto(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getObject("due_date", LocalDate.class), TaskPriority.valueOf(rs.getString("priority")),
                rs.getObject("owner_id", UUID.class), rs.getBoolean("done"),
                doneAt == null ? null : doneAt.toInstant(), rs.getObject("deal_id", UUID.class),
                rs.getString("vessel_name"), rs.getObject("contact_id", UUID.class), rs.getString("contact_name"),
                rs.getObject("company_id", UUID.class));
    }

    public record TaskDto(UUID id, String title, LocalDate dueDate, TaskPriority priority, UUID ownerId, boolean done,
            Instant doneAt, UUID dealId, String dealVessel, UUID contactId, String contactName, UUID companyId) {
    }

    public record TaskRequest(
            @NotBlank(message = "Le titre est obligatoire.") @Size(max = 200, message = "200 caractères maximum.")
            String title,
            @NotNull(message = "L'échéance est obligatoire.") LocalDate dueDate,
            @NotNull(message = "La priorité est obligatoire.") TaskPriority priority,
            UUID ownerId,
            UUID dealId,
            UUID contactId,
            UUID companyId) {
    }

    public record DoneRequest(boolean done) {
    }
}
