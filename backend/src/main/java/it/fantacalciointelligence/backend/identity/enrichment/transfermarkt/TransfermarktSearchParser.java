package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class TransfermarktSearchParser {

    private static final Pattern PLAYER_ID =
            Pattern.compile("/profil/spieler/([1-9][0-9]*)");

    public List<TransfermarktCandidate> parse(
            String html,
            String baseUri
    ) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Document document =
                Jsoup.parse(
                        html,
                        baseUri == null ? "" : baseUri
                );

        Map<String, TransfermarktCandidate> candidates =
                new LinkedHashMap<>();

        for (Element link :
                document.select(
                        "a[href*=\"/profil/spieler/\"]"
                )) {

            String href = link.absUrl("href");
            if (href == null || href.isBlank()) {
                href = link.attr("href");
            }

            Matcher matcher =
                    PLAYER_ID.matcher(href);

            if (!matcher.find()) {
                continue;
            }

            String id = matcher.group(1);

            String name =
                    firstNonBlank(
                            link.attr("title"),
                            link.text()
                    );

            if (name == null || name.isBlank()) {
                continue;
            }

            Element row = link.closest("tr");

            String context =
                    row == null
                            ? firstNonBlank(
                                    link.parent() == null
                                            ? null
                                            : link.parent().text(),
                                    ""
                            )
                            : row.text();

            candidates.putIfAbsent(
                    id,
                    new TransfermarktCandidate(
                            id,
                            name.trim(),
                            href,
                            context == null
                                    ? ""
                                    : context.trim()
                    )
            );
        }

        return List.copyOf(
                candidates.values()
        );
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}