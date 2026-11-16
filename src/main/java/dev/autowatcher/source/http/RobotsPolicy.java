package dev.autowatcher.source.http;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative robots policy: combines wildcard and explicitly named AutoWatcher groups. */
public final class RobotsPolicy {
    private RobotsPolicy() {}
    public static boolean allows(String robots, URI uri) {
        List<String> agents = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        boolean directives = false;
        for (String raw : robots.split("\\R")) {
            String line = raw.split("#",2)[0].strip();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0,colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon+1).strip();
            if (key.equals("user-agent")) {
                if (directives) { agents.clear(); directives=false; }
                agents.add(value.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow") || key.equals("disallow")) {
                directives = true;
                if (!value.isEmpty() && agents.stream().anyMatch(a -> a.equals("*") || a.contains("autowatcher")))
                    rules.add(new Rule(value,key.equals("allow")));
            }
        }
        String target = uri.getRawPath() + (uri.getRawQuery()==null ? "" : "?" + uri.getRawQuery());
        Rule best = null;
        for (Rule rule : rules) {
            StringBuilder regex = new StringBuilder("^");
            for (int i=0;i<rule.path().length();i++) {
                char ch=rule.path().charAt(i);
                if(ch=='*') regex.append(".*");
                else if(ch=='$' && i==rule.path().length()-1) regex.append("$");
                else regex.append(Pattern.quote(String.valueOf(ch)));
            }
            if(Pattern.compile(regex.toString()).matcher(target).find()
                    && (best==null || rule.path().length()>best.path().length()
                    || rule.path().length()==best.path().length() && rule.allow())) best=rule;
        }
        return best==null || best.allow();
    }
    private record Rule(String path, boolean allow) {}
}
