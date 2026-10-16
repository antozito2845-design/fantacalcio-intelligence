package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class TransfermarktProfileParser {

    private static final Pattern PLAYER_ID =
            Pattern.compile("/profil/spieler/([1-9][0-9]*)");

    private static final Pattern DATE_DMY =
            Pattern.compile(
                    "(?i)(?:Date of birth/Age|Date of birth|Geburtsdatum|Data di nascita)\\s*:\\s*"
                            + "(\\d{2}[./]\\d{2}[./]\\d{4})"
            );

    private static final Pattern HEIGHT =
            Pattern.compile(
                    "(?i)(?:Height|GrÃ¶ÃŸe|Altezza)\\s*:\\s*"
                            + "([12][,.]\\d{2})\\s*m"
            );

    private static final Pattern CITIZENSHIP =
            Pattern.compile(
                    "(?i)(?:Citizenship|StaatsbÃ¼rgerschaft|NazionalitÃ )\\s*:\\s*"
                            + "(.+?)(?=\\s+(?:Height|Position|Foot|Player agent|Current club|Joined|Contract expires|Date of death)\\s*:|$)"
            );

    private static final Pattern FOOT =
            Pattern.compile(
                    "(?i)(?:Foot|FuÃŸ|Piede)\\s*:\\s*"
                            + "(right|left|both|rechts|links|beidfÃ¼ÃŸig|destro|sinistro|entrambi)"
            );

    private static final Pattern CURRENT_CLUB =
            Pattern.compile(
                    "(?i)Current club\\s*:\\s*(.+?)"
                            + "(?=\\s+(?:Joined|Contract expires|Outfitter|Social-Media)\\s*:|$)"
            );

    public TransfermarktProfile parse(
            String html,
            String profileUrl
    ) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException(
                    "HTML profilo Transfermarkt vuoto"
            );
        }

        Document document =
                Jsoup.parse(
                        html,
                        profileUrl == null ? "" : profileUrl
                );

        String body =
                clean(
                        document.body() == null
                                ? document.text()
                                : document.body().text()
                );

        String fullName =
                cleanName(
                        firstNonBlank(
                                text(
                                        document,
                                        "h1.data-header__headline-wrapper"
                                ),
                                attr(
                                        document,
                                        "meta[property=og:title]",
                                        "content"
                                )
                        )
                );

        LocalDate birthDate =
                parseDate(
                        group(
                                DATE_DMY,
                                body
                        )
                );

        Integer heightCm =
                parseHeight(
                        group(
                                HEIGHT,
                                body
                        )
                );

        String nationality =
                normalizeCitizenship(
                        group(
                                CITIZENSHIP,
                                body
                        )
                );

        String preferredFoot =
                normalizeFoot(
                        group(
                                FOOT,
                                body
                        )
                );

        String currentClub =
                cleanClub(
                        firstNonBlank(
                                text(
                                        document,
                                        "span.data-header__club"
                                ),
                                text(
                                        document,
                                        "div.data-header__club-info span"
                                ),
                                text(
                                        document,
                                        "div.data-header__club-info a"
                                ),
                                group(
                                        CURRENT_CLUB,
                                        body
                                )
                        )
                );

        return new TransfermarktProfile(
                playerId(profileUrl),
                fullName,
                birthDate,
                nationality,
                heightCm,
                preferredFoot,
                currentClub,
                profileUrl
        );
    }

    private String playerId(String url) {
        if (url == null) {
            return null;
        }

        Matcher matcher =
                PLAYER_ID.matcher(url);

        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private String group(
            Pattern pattern,
            String input
    ) {
        if (input == null) {
            return null;
        }

        Matcher matcher =
                pattern.matcher(input);

        return matcher.find()
                ? clean(matcher.group(1))
                : null;
    }

    private LocalDate parseDate(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.replace('.', '/');

        try {
            return LocalDate.parse(
                    normalized,
                    DateTimeFormatter.ofPattern(
                            "dd/MM/yyyy",
                            Locale.ROOT
                    )
            );
        }
        catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Integer parseHeight(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.replace(',', '.');

        try {
            double metres =
                    Double.parseDouble(
                            normalized
                    );

            return (int) Math.round(
                    metres * 100.0
            );
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeCitizenship(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String cleaned =
                value.replaceAll(
                                "(?i)Image:\\s*",
                                ""
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        /*
         * Transfermarkt may expose multiple citizenships.
         * Keep the source text rather than inventing one.
         */
        return cleaned.isBlank()
                ? null
                : cleaned;
    }

    private String normalizeFoot(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                TransfermarktCandidateMatcher
                        .normalize(value);

        if (normalized.contains("right")
                || normalized.contains("rechts")
                || normalized.contains("destro")) {
            return "RIGHT";
        }

        if (normalized.contains("left")
                || normalized.contains("links")
                || normalized.contains("sinistro")) {
            return "LEFT";
        }

        if (normalized.contains("both")
                || normalized.contains("beidf")
                || normalized.contains("entrambi")) {
            return "BOTH";
        }

        return null;
    }

    private String cleanName(
            String value
    ) {
        String cleaned =
                clean(value);

        if (cleaned == null) {
            return null;
        }

        String normalized =
                cleaned
                        .replaceFirst(
                                "^#\\d+\\s+",
                                ""
                        )
                        .replaceAll(
                                "\\s*-\\s*Player profile.*$",
                                ""
                        )
                        .replaceAll(
                                "\\s*\\|\\s*Transfermarkt.*$",
                                ""
                        )
                        .trim();

        return repairMojibake(normalized);
    }

    private String cleanClub(
            String value
    ) {
        String cleaned =
                clean(value);

        if (cleaned == null) {
            return null;
        }

        String normalized =
                repairMojibake(
                        cleaned
                                .replaceFirst(
                                        "(?i)\\s+Serie A.*$",
                                        ""
                                )
                                .trim()
                );

        if (normalized == null
                || normalized.length() > 100
                || normalized.contains("Transfermarkt Videos")
                || normalized.contains("Quick Links")
                || normalized.contains("Legal notice")) {
            return null;
        }

        return normalized;
    }

    String repairMojibake(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return value;
        }

        int originalScore =
                mojibakeScore(value);

        if (originalScore == 0) {
            return value;
        }

        try {
            byte[] bytes =
                    value.getBytes(
                            Charset.forName(
                                    "windows-1252"
                            )
                    );

            String repaired =
                    new String(
                            bytes,
                            StandardCharsets.UTF_8
                    );

            if (repaired.indexOf('\uFFFD') >= 0) {
                return value;
            }

            int repairedScore =
                    mojibakeScore(repaired);

            return repairedScore < originalScore
                    ? repaired
                    : value;
        }
        catch (RuntimeException exception) {
            return value;
        }
    }

    private int mojibakeScore(
            String value
    ) {
        int score = 0;

        for (int index = 0;
                index < value.length();
                index++) {

            char character =
                    value.charAt(index);

            /*
             * Common UTF-8 -> Windows-1252 mojibake lead
             * characters, expressed numerically so the Java
             * source itself is encoding-independent.
             */
            if (character == '\u00C2'
                    || character == '\u00C3'
                    || character == '\u00C4'
                    || character == '\u00C5') {
                score += 2;
            }

            /*
             * C1 control range is another strong corruption
             * signal after an incorrect character decoding.
             */
            if (character >= '\u0080'
                    && character <= '\u009F') {
                score += 3;
            }
        }

        return score;
    }
    private String text(
            Document document,
            String selector
    ) {
        Element element =
                document.selectFirst(
                        selector
                );

        return element == null
                ? null
                : clean(
                        element.text()
                );
    }

    private String attr(
            Document document,
            String selector,
            String attribute
    ) {
        Element element =
                document.selectFirst(
                        selector
                );

        return element == null
                ? null
                : clean(
                        element.attr(
                                attribute
                        )
                );
    }

    private String firstNonBlank(
            String... values
    ) {
        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String clean(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String cleaned =
                value
                        .replace('\u00A0', ' ')
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        return cleaned.isBlank()
                ? null
                : cleaned;
    }
}