"""Public access probe. Never bypasses redirects, authentication, CAPTCHA or robots restrictions."""
import json
import time
import urllib.error
import urllib.request
import urllib.robotparser

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

opener = urllib.request.build_opener(NoRedirect)
agent = "AutoWatcher/0.2 (+local personal listing monitor)"
targets = [
    ("AVITO", "https://www.avito.ru/robots.txt", "https://www.avito.ru/rossiya/avtomobili"),
    ("AUTO_RU", "https://auto.ru/robots.txt", "https://auto.ru/cars/used/"),
]
results = []
def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": agent})
    with opener.open(request, timeout=20) as response:
        return response.status, response.read(5 * 1024 * 1024).decode("utf-8", "replace")

for source, robots_url, page_url in targets:
    item = {"source": source, "url": page_url}
    try:
        status, robots = get(robots_url)
        policy = urllib.robotparser.RobotFileParser()
        policy.parse(robots.splitlines())
        item["robots_http"] = status
        if not policy.can_fetch(agent, page_url):
            item["result"] = "ROBOTS_DENIED"
        else:
            time.sleep(5)
            status, html = get(page_url)
            lower = html.lower()
            item["page_http"] = status
            blocked = any(word in lower for word in ["smartcaptcha", "captcha-container", "доступ временно ограничен", "подтвердите, что вы не робот"])
            item["result"] = "ACCESS_CHALLENGE" if blocked else "HTML_AVAILABLE_NOT_PARSER_VERIFICATION"
            item["has_json_ld"] = "application/ld+json" in lower
            item["has_card_markers"] = 'data-marker="item"' in lower or "listingitem" in lower
    except urllib.error.HTTPError as error:
        item["result"] = "HTTP_" + str(error.code)
    except Exception as error:
        item["result"] = type(error).__name__
    results.append(item)
print(json.dumps(results, ensure_ascii=False, indent=2))
with open("source-probe.json", "w", encoding="utf-8") as output:
    json.dump(results, output, ensure_ascii=False, indent=2)
