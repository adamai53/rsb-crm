package dj.redseabunkering.crm.demo;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Jeu de données du prototype ; les dates sont des décalages en jours par rapport au jour J. */
final class DemoData {

    record OwnerRow(String key, String first, String last, String email) {
    }

    record CompanyRow(String key, String name, String type, String country, String city, int fleet, String web) {
    }

    record ContactRow(String key, String first, String last, String title, String company, String lifecycle,
            String source, String owner) {
    }

    record DealRow(String vessel, String imo, String company, String contact, String grade, int qty, String stage,
            String owner, String source, int eta, int created) {
    }

    record LeadRow(String name, String company, String email, String phone, String vessel, String imo, String grade,
            int qty, int eta, String location, String source, String message, int minutesAgo, String status) {
    }

    record ActivityRow(String type, String text, int day, String by, String deal, String contact, String company) {
    }

    record TaskRow(String title, int due, String owner, String priority, String deal, String contact, String company,
            boolean done) {
    }

    record QuoteRow(String number, String deal, int created, String status, int price, int barge) {
    }

    record SentRow(String contact, String deal, String subject, int day, String status) {
    }

    record CampaignRow(String name, String channel, int spend, long impressions, long clicks) {
    }

    static final List<OwnerRow> OWNERS = List.of(
            new OwnerRow("u1", "Hassan", "Doualeh", "hassan.doualeh@rsb.local"),
            new OwnerRow("u2", "Fatouma", "Ali", "fatouma.ali@rsb.local"),
            new OwnerRow("u3", "Ibrahim", "Omar", "ibrahim.omar@rsb.local"));

    static final List<CompanyRow> COMPANIES = List.of(
            new CompanyRow("c1", "Kestrel Bulk Carriers", "ARMATEUR", "Grèce", "Le Pirée", 14, "kestrelbulk.example"),
            new CompanyRow("c2", "Nordvik Tankers AS", "ARMATEUR", "Norvège", "Bergen", 22, "nordvik.example"),
            new CompanyRow("c3", "Horn Africa Maritime Agency", "AGENT_MARITIME", "Djibouti", "Djibouti", 0, "hornafrica-agency.example"),
            new CompanyRow("c4", "Indigo Ocean Ship Management", "SHIP_MANAGER", "Inde", "Mumbai", 38, "indigo-osm.example"),
            new CompanyRow("c5", "Levant Star Shipping", "ARMATEUR", "Émirats arabes unis", "Dubaï", 9, "levantstar.example"),
            new CompanyRow("c6", "Aurora Reefer Lines", "ARMATEUR", "Danemark", "Copenhague", 11, "aurorareefer.example"),
            new CompanyRow("c7", "Meridian Fuel Trading", "TRADER", "Singapour", "Singapour", 0, "meridianfuel.example"),
            new CompanyRow("c8", "Tadjoura Marine Services", "AGENT_MARITIME", "Djibouti", "Djibouti", 0, "tadjoura-marine.example"),
            new CompanyRow("c9", "Pacifica Container Lines", "ARMATEUR", "Chine", "Shanghai", 46, "pacifica-cl.example"),
            new CompanyRow("c10", "Bab-el-Mandeb Shipping", "ARMATEUR", "Arabie saoudite", "Djeddah", 7, "babelmandeb.example"),
            new CompanyRow("c11", "Halcyon Offshore", "SHIP_MANAGER", "Royaume-Uni", "Aberdeen", 16, "halcyon-offshore.example"),
            new CompanyRow("c12", "Sahel Logistics Group", "AFFRETEUR", "Éthiopie", "Addis-Abeba", 0, "sahel-logistics.example"));

    static final List<ContactRow> CONTACTS = List.of(
            new ContactRow("ct1", "Nikos", "Papadakis", "Responsable soutage", "c1", "CLIENT", "Recommandation", "u1"),
            new ContactRow("ct2", "Eleni", "Kostas", "Opérations flotte", "c1", "CLIENT", "Recommandation", "u1"),
            new ContactRow("ct3", "Lars", "Haugen", "Bunker buyer", "c2", "CLIENT", "LinkedIn", "u2"),
            new ContactRow("ct4", "Ingrid", "Solberg", "Directrice achats", "c2", "CLIENT", "LinkedIn", "u2"),
            new ContactRow("ct5", "Abdourahman", "Youssouf", "Agent principal", "c3", "OPPORTUNITE", "Appel entrant", "u3"),
            new ContactRow("ct6", "Hodan", "Ismaël", "Opérations portuaires", "c3", "LEAD", "Recommandation", "u3"),
            new ContactRow("ct7", "Rohan", "Mehta", "Fuel procurement manager", "c4", "CLIENT", "Site web", "u1"),
            new ContactRow("ct8", "Priya", "Nair", "Superintendante technique", "c4", "CLIENT", "Site web", "u1"),
            new ContactRow("ct9", "Omar", "Al-Mansoori", "Directeur commercial", "c5", "CLIENT", "Google Ads", "u3"),
            new ContactRow("ct10", "Mette", "Larsen", "Bunker trader", "c6", "CLIENT", "Site web", "u2"),
            new ContactRow("ct11", "Wei", "Tan", "Trader carburants marins", "c7", "MQL", "LinkedIn", "u3"),
            new ContactRow("ct12", "Daniel", "Lim", "Crédit & risque", "c7", "LEAD", "LinkedIn", "u3"),
            new ContactRow("ct13", "Moussa", "Djama", "Gérant", "c8", "SQL", "Appel entrant", "u3"),
            new ContactRow("ct14", "Chen", "Hao", "Bunker procurement", "c9", "CLIENT", "Salon / événement", "u1"),
            new ContactRow("ct15", "Lin", "Zhou", "Marine superintendent", "c9", "CLIENT", "Salon / événement", "u3"),
            new ContactRow("ct16", "Faisal", "Al-Harbi", "Responsable flotte", "c10", "OPPORTUNITE", "Appel entrant", "u3"),
            new ContactRow("ct17", "James", "Whitmore", "Fleet fuel manager", "c11", "CLIENT", "Site web", "u2"),
            new ContactRow("ct18", "Sarah", "Mackay", "Achats", "c11", "CLIENT", "Recommandation", "u2"),
            new ContactRow("ct19", "Tesfaye", "Bekele", "Logistique", "c12", "OPPORTUNITE", "Meta Ads", "u2"),
            new ContactRow("ct20", "Amina", "Hassan", "Coordination maritime", "c12", "MQL", "Meta Ads", "u2"));

    static final List<DealRow> DEALS = List.of(
            new DealRow("MV Kestrel Dawn", "9734512", "c1", "ct1", "VLSFO", 850, "NEGOCIATION", "u1", "Recommandation", 6, -9),
            new DealRow("MT Nordvik Spirit", "9612087", "c2", "ct3", "MGO", 180, "COTATION", "u2", "LinkedIn", 4, -5),
            new DealRow("MV Indigo Pearl", "9801233", "c4", "ct7", "VLSFO", 1200, "NOMINATION", "u1", "Site web", 2, -12),
            new DealRow("MV Levant Star", "9547761", "c5", "ct9", "HSFO", 950, "QUALIFIEE", "u3", "Google Ads", 11, -3),
            new DealRow("MV Aurora Frost", "9688420", "c6", "ct10", "LSMGO", 220, "RECUE", "u2", "Site web", 9, -1),
            new DealRow("MV Pacifica Harmony", "9873345", "c9", "ct14", "VLSFO", 1500, "COTATION", "u1", "Salon / événement", 8, -6),
            new DealRow("MV Bab Al Salam", "9450129", "c10", "ct16", "HSFO", 700, "RECUE", "u3", "Appel entrant", 14, 0),
            new DealRow("MV Halcyon Tide", "9765098", "c11", "ct17", "MGO", 300, "NEGOCIATION", "u2", "Site web", 5, -7),
            new DealRow("MV Kestrel Arrow", "9734524", "c1", "ct2", "VLSFO", 900, "LIVREE", "u1", "Recommandation", -6, -20),
            new DealRow("MT Nordvik Crest", "9612099", "c2", "ct4", "VLSFO", 1100, "LIVREE", "u2", "LinkedIn", -18, -30),
            new DealRow("MV Indigo Breeze", "9801245", "c4", "ct8", "MGO", 250, "LIVREE", "u1", "Site web", -33, -41),
            new DealRow("MV Pacifica Unity", "9873357", "c9", "ct15", "VLSFO", 1400, "LIVREE", "u3", "Salon / événement", -47, -60),
            new DealRow("MV Levant Dune", "9547773", "c5", "ct9", "HSFO", 800, "LIVREE", "u3", "Google Ads", -64, -72),
            new DealRow("MV Aurora Polar", "9688432", "c6", "ct10", "LSMGO", 200, "LIVREE", "u2", "Site web", -79, -85),
            new DealRow("MV Halcyon Reef", "9765100", "c11", "ct18", "VLSFO", 750, "LIVREE", "u2", "Recommandation", -96, -104),
            new DealRow("MV Kestrel Bay", "9734536", "c1", "ct1", "VLSFO", 1000, "LIVREE", "u1", "Recommandation", -118, -125),
            new DealRow("MV Pacifica Grace", "9873369", "c9", "ct14", "HSFO", 1300, "LIVREE", "u1", "E-mail direct", -140, -150),
            new DealRow("MV Bab Al Noor", "9450131", "c10", "ct16", "MGO", 150, "PERDUE", "u3", "Meta Ads", -10, -22),
            new DealRow("MV Indigo Star", "9801257", "c4", "ct7", "VLSFO", 1000, "PERDUE", "u1", "Site web", -25, -35),
            new DealRow("MV Levant Pearl", "9547785", "c5", "ct9", "VLSFO", 600, "QUALIFIEE", "u3", "Site web", 17, -2),
            new DealRow("MV Sahel Trader", "9321456", "c12", "ct19", "MGO", 120, "RECUE", "u2", "Meta Ads", 20, -1),
            new DealRow("MV Horn Express", "9512340", "c3", "ct5", "VLSFO", 500, "COTATION", "u3", "Appel entrant", 3, -4));

    private static final int[] PRICE_VARIATIONS = { 0, 4, -6, 9, -3, 7, -8, 2, 5, -4, 10, -2, 6, -7, 3, -5, 8, 1, -9, 4, -1, 6 };
    private static final Map<String, Integer> REFERENCE_PRICES = Map.of("VLSFO", 612, "HSFO", 468, "MGO", 845, "LSMGO", 862);

    static final List<LeadRow> LEADS = List.of(
            new LeadRow("Karim Haddad", "Corniche Shipping Ltd", "k.haddad@corniche-shipping.example", "+971 50 000 1122", "MV Corniche Wave", "9667712", "VLSFO", 650, 7, "ANCHORAGE", "Site web", "Besoin d'une cotation VLSFO pour une escale technique, livraison par barge.", 18, "NOUVEAU"),
            new LeadRow("Anders Holm", "Fjord Reefer AS", "anders.holm@fjordreefer.example", "+47 00 00 1180", "MV Fjord Crystal", "9701188", "MGO", 140, 5, "DMP", "LinkedIn", "Pouvez-vous livrer du MGO pendant les opérations commerciales ?", 95, "NOUVEAU"),
            new LeadRow("Mariam Said", "Coastal Lines Berbera", "m.said@coastal-berbera.example", "+252 00 000 314", "MV Berbera Sun", "9388410", "HSFO", 400, 10, "ANCHORAGE", "Google Ads", "Navire équipé de scrubber. Prix HSFO pour 400 MT ?", 260, "NOUVEAU"),
            new LeadRow("Takeshi Ono", "Sakura Bulk Co.", "t.ono@sakurabulk.example", "+81 00 0000 2231", "MV Sakura Maru", "9590021", "VLSFO", 1000, 12, "ANCHORAGE", "Site web", "Escale de soutage sur la route Suez. Merci de confirmer disponibilité et prix.", 1300, "NOUVEAU"),
            new LeadRow("Ali Robleh", "Doraleh Logistics", "ali.robleh@doraleh-log.example", "+253 77 00 00 61", "Remorqueur Ras Siyan", null, "MGO", 60, 3, "DMP", "Meta Ads", "Petit volume de MGO pour remorqueur.", 2900, "NOUVEAU"),
            new LeadRow("Test formulaire", null, "test@test.example", null, null, null, "VLSFO", 1, 1, null, "Site web", "test", 4300, "REJETE"));

    private static ActivityRow onDeal(String type, String text, int day, String by, String deal) {
        return new ActivityRow(type, text, day, by, deal, null, null);
    }

    static final List<ActivityRow> ACTIVITIES = List.of(
            onDeal("NOTE", "Le client veut un prix livré par barge au mouillage, paiement à 30 jours.", -8, "u1", "d1"),
            onDeal("EMAIL", "Cotation v1 envoyée : 618 USD/MT, validité 48 h.", -6, "u1", "d1"),
            onDeal("CALL", "Négociation : le client vise 605 USD/MT. Contre-proposition à 612 USD/MT avec frais de barge offerts.", -2, "u1", "d1"),
            onDeal("EMAIL", "Nomination reçue de Rohan Mehta pour 1 200 MT VLSFO.", -3, "u1", "d3"),
            onDeal("NOTE", "Barge réservée pour le 12 oct., créneau 06:00–12:00. Échantillons MARPOL à prévoir.", -1, "u1", "d3"),
            onDeal("EMAIL", "Cotation MGO 180 MT envoyée.", -3, "u2", "d2"),
            onDeal("MEETING", "Rencontre au salon : besoin récurrent sur la rotation Asie–Europe via Suez.", -6, "u1", "d6"),
            onDeal("EMAIL", "Cotation 1 500 MT VLSFO envoyée, attente retour du service achats.", -4, "u1", "d6"),
            onDeal("CALL", "Appel avec James Whitmore : demande de délai de paiement à 45 jours.", -3, "u2", "d8"),
            onDeal("NOTE", "Livraison effectuée, BDN signé par le chef mécanicien. 900 MT livrées.", -6, "u1", "d9"),
            onDeal("CALL", "Demande reçue par téléphone via l'agence pour MV Horn Express.", -4, "u3", "d22"),
            new ActivityRow("NOTE", "Contact recommandé par Abdourahman Youssouf (agence).", -10, "u3", null, "ct6", "c3"),
            new ActivityRow("EMAIL", "Newsletter « prix hebdo » ouverte 3 fois.", -5, "u3", null, "ct11", "c7"),
            new ActivityRow("CALL", "Premier échange : Meridian achète pour des affréteurs tiers, à suivre.", -12, "u3", null, "ct11", "c7"));

    static final List<TaskRow> TASKS = List.of(
            new TaskRow("Envoyer la cotation révisée à Kestrel", -1, "u1", "HAUTE", "d1", null, null, false),
            new TaskRow("Appeler Lars Haugen : retour sur la cotation MGO", 0, "u2", "HAUTE", "d2", null, null, false),
            new TaskRow("Confirmer la barge pour MV Indigo Pearl", 0, "u1", "HAUTE", "d3", null, null, false),
            new TaskRow("Qualifier la demande MV Bab Al Salam", 1, "u3", "MOYENNE", "d7", null, null, false),
            new TaskRow("Préparer BDN et certificat qualité MV Indigo Pearl", 2, "u1", "MOYENNE", "d3", null, null, false),
            new TaskRow("Vérifier la limite de crédit de Meridian Fuel Trading", 2, "u3", "MOYENNE", null, "ct12", "c7", false),
            new TaskRow("Relancer Pacifica sur la cotation 1 500 MT", 3, "u1", "MOYENNE", "d6", null, null, false),
            new TaskRow("Réunion mensuelle avec les agents maritimes", 5, "u3", "BASSE", null, "ct5", "c3", false),
            new TaskRow("Envoyer la facture MV Kestrel Arrow", -5, "u1", "HAUTE", "d9", null, null, true));

    static final List<QuoteRow> QUOTES = List.of(
            new QuoteRow("0141", "d1", -6, "ENVOYE", 618, 1450),
            new QuoteRow("0142", "d6", -4, "ENVOYE", 610, 1450),
            new QuoteRow("0143", "d3", -5, "ACCEPTE", 606, 0));
    static final int LAST_QUOTE_NUMBER = 143;

    static final List<SentRow> SENT = List.of(
            new SentRow("ct1", "d1", "Cotation VLSFO 0,5 % S — MV Kestrel Dawn", -6, "OUVERT"),
            new SentRow("ct3", "d2", "Cotation MGO DMA 0,1 % S — MT Nordvik Spirit", -3, "OUVERT"),
            new SentRow("ct14", "d6", "Cotation VLSFO 0,5 % S — MV Pacifica Harmony", -4, "ENVOYE"),
            new SentRow("ct7", "d3", "Nomination confirmée — MV Indigo Pearl", -3, "OUVERT"));

    static final List<CampaignRow> CAMPAIGNS = List.of(
            new CampaignRow("Recherche « bunker Djibouti »", "Google Ads", 2400, 48200, 1930),
            new CampaignRow("Décideurs achats carburant", "LinkedIn", 3100, 61000, 870),
            new CampaignRow("Notoriété Mer Rouge", "Meta Ads", 900, 120400, 2100),
            new CampaignRow("SEO et refonte redseabunkering.com", "Site web", 1800, 35600, 4120),
            new CampaignRow("Salon maritime régional", "Salon / événement", 6500, 0, 0),
            new CampaignRow("Newsletter prix hebdo", "E-mail direct", 150, 2400, 610));

    static final String ENROLLED_CONTACT = "ct14";

    private static final Map<String, String> PHONE_PREFIXES = Map.ofEntries(Map.entry("Grèce", "+30"),
            Map.entry("Norvège", "+47"), Map.entry("Djibouti", "+253"), Map.entry("Inde", "+91"),
            Map.entry("Émirats arabes unis", "+971"), Map.entry("Danemark", "+45"), Map.entry("Singapour", "+65"),
            Map.entry("Chine", "+86"), Map.entry("Arabie saoudite", "+966"), Map.entry("Royaume-Uni", "+44"),
            Map.entry("Éthiopie", "+251"));

    private DemoData() {
    }

    static LocalDate reference(String configured, LocalDate today) {
        return configured == null || configured.isBlank() || configured.equalsIgnoreCase("today") ? today
                : LocalDate.parse(configured.trim());
    }

    static CompanyRow company(String key) {
        return COMPANIES.stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    /** {@code key} : « d1 » à « d22 », dans l'ordre de {@link #DEALS}. */
    static DealRow deal(String key) {
        return DEALS.get(Integer.parseInt(key.substring(1)) - 1);
    }

    static BigDecimal price(String grade, int index) {
        return BigDecimal.valueOf(REFERENCE_PRICES.get(grade) + PRICE_VARIATIONS[index]);
    }

    static String lostReason(int index, String stage) {
        if (!stage.equals("PERDUE")) {
            return null;
        }
        return index == 17 ? "Prix concurrent inférieur" : "Escale annulée par l'armateur";
    }

    static String email(String first, String last, String domain) {
        String local = Normalizer.normalize(first.charAt(0) + "." + last, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-zA-Z0-9.-]", "").toLowerCase(Locale.ROOT);
        return local + "@" + domain;
    }

    static String phone(String country, int index) {
        return PHONE_PREFIXES.getOrDefault(country, "+00") + " 00 00 " + (index + 11);
    }
}
