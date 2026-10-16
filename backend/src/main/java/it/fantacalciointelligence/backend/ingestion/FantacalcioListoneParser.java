package it.fantacalciointelligence.backend.ingestion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.common.BadRequestException;

@Component
public class FantacalcioListoneParser {

    private static final Pattern ROW = Pattern.compile(
            "<tr\\s+class=\"player-row\"(?<attrs>.*?)>(?<body>.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern CLASSIC_ROLE = Pattern.compile(
            "data-filter-role-classic=\"([pdca])\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PLAYER_LINK = Pattern.compile(
            "href=\"https://www\\.fantacalcio\\.it/serie-a/squadre/"
                    + "([^/\"?#]+)/([^/\"]+)/(\\d+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PLAYER_NAME = Pattern.compile(
            "<a[^>]*class=\"[^\"]*player-name[^\"]*player-link[^\"]*\"[^>]*>"
                    + ".*?<span>(.*?)</span>.*?</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern CURRENT_PRICE = Pattern.compile(
            "<td[^>]*class=\"[^\"]*player-classic-current-price[^\"]*\"[^>]*>"
                    + "(.*?)</td>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern FVM = Pattern.compile(
            "<td[^>]*class=\"[^\"]*player-classic-fvm[^\"]*\"[^>]*>"
                    + "(.*?)</td>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /*
     * Fantacalcio uses this semantic marker for players that are
     * still visible in the listone but no longer play in Serie A:
     *
     * <span class="out-of-game"
     *       title="Non gioca più in Serie A">*</span>
     *
     * Do not infer the state from a generic asterisk: the CSS class
     * is the authoritative marker exposed by the source.
     */
    private static final Pattern OUT_OF_GAME = Pattern.compile(
            "<span[^>]*class=\"[^\"]*\\bout-of-game\\b[^\"]*\"[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private static final Pattern NUMERIC_HTML_ENTITY = Pattern.compile(
            "&#(?:x([0-9a-fA-F]+)|([0-9]+));"
    );

    public List<ListonePlayer> parse(String html) {
        if (html == null || html.isBlank()) {
            throw new BadRequestException("HTML listone Fantacalcio vuoto");
        }

        List<ListonePlayer> result = new ArrayList<>();
        Matcher rows = ROW.matcher(html);

        while (rows.find()) {
            String attrs = rows.group("attrs");
            String body = rows.group("body");

            Matcher roleMatcher = CLASSIC_ROLE.matcher(attrs);
            Matcher linkMatcher = PLAYER_LINK.matcher(body);
            Matcher nameMatcher = PLAYER_NAME.matcher(body);

            if (!roleMatcher.find() || !linkMatcher.find() || !nameMatcher.find()) {
                continue;
            }

            String role = roleMatcher.group(1).toUpperCase(Locale.ROOT);

            String clubSlug = clean(linkMatcher.group(1))
                    .toLowerCase(Locale.ROOT);

            String playerSlug = clean(linkMatcher.group(2))
                    .toLowerCase(Locale.ROOT);

            String numericId = linkMatcher.group(3);

            String name = clean(nameMatcher.group(1));

            BigDecimal quotation = decimal(
                    firstGroup(CURRENT_PRICE, body)
            );

            BigDecimal fmv = decimal(
                    firstGroup(FVM, body)
            );

            boolean outOfGame =
                    OUT_OF_GAME.matcher(body).find();

            if (name.isBlank()) {
                throw new BadRequestException(
                        "Riga Fantacalcio con nome vuoto: " + numericId
                );
            }

            result.add(new ListonePlayer(
                    "FANTACALCIO-" + numericId,
                    numericId,
                    name,
                    clubSlug,
                    playerSlug,
                    role,
                    quotation,
                    fmv,
                    outOfGame
            ));
        }

        if (result.size() < 300) {
            throw new BadRequestException(
                    "Parsing listone Fantacalcio sospetto: trovati solo "
                            + result.size() + " giocatori"
            );
        }

        long distinctIds = result.stream()
                .map(ListonePlayer::externalId)
                .distinct()
                .count();

        if (distinctIds != result.size()) {
            throw new BadRequestException(
                    "Listone Fantacalcio contiene ID duplicati"
            );
        }

        return List.copyOf(result);
    }

    private String firstGroup(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);

        return matcher.find()
                ? clean(matcher.group(1))
                : null;
    }

    private BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return null;
        }

        try {
            return new BigDecimal(
                    raw.replace(",", ".").trim()
            );
        } catch (NumberFormatException exception) {
            throw new BadRequestException(
                    "Valore numerico Fantacalcio non valido: " + raw,
                    exception
            );
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }

        String text = TAG.matcher(value).replaceAll("");

        text = decodeNumericHtmlEntities(text);

        return text
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String decodeNumericHtmlEntities(String value) {
        Matcher matcher = NUMERIC_HTML_ENTITY.matcher(value);

        StringBuffer output = new StringBuffer();

        while (matcher.find()) {
            try {
                int codePoint;

                if (matcher.group(1) != null) {
                    codePoint = Integer.parseInt(
                            matcher.group(1),
                            16
                    );
                } else {
                    codePoint = Integer.parseInt(
                            matcher.group(2)
                    );
                }

                String decoded =
                        new String(Character.toChars(codePoint));

                matcher.appendReplacement(
                        output,
                        Matcher.quoteReplacement(decoded)
                );

            } catch (IllegalArgumentException exception) {
                matcher.appendReplacement(
                        output,
                        Matcher.quoteReplacement(
                                matcher.group()
                        )
                );
            }
        }

        matcher.appendTail(output);

        return output.toString();
    }

    public record ListonePlayer(
            String externalId,
            String numericFantacalcioId,
            String name,
            String clubSlug,
            String playerSlug,
            String role,
            BigDecimal quotation,
            BigDecimal fmv,
            boolean outOfGame
    ) {
    }
}