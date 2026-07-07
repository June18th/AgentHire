import fs from "node:fs/promises";
import path from "node:path";

const PORT = Number(process.env.BOSS_DEBUG_PORT || "9222");
const ROOT = process.cwd();
const OUT_DIR = path.join(ROOT, "workspace", "datas");
const OUT_JSON = path.join(OUT_DIR, "boss-jobs.json");
const OUT_CSV = path.join(OUT_DIR, "boss-jobs.csv");

const cityCodes = new Map([
  ["北京", "101010100"],
  ["天津", "101030100"],
  ["杭州", "101210100"],
  ["深圳", "101280600"],
  ["郑州", "101180100"],
  ["成都", "101270100"],
  ["南京", "101190100"],
  ["武汉", "101200100"],
  ["西安", "101110100"],
  ["苏州", "101190400"],
  ["合肥", "101220100"],
  ["长沙", "101250100"],
]);

const queries = (process.env.BOSS_QUERIES || "开发,后端开发,Java,前端开发")
  .split(/[,，]/)
  .map((item) => item.trim())
  .filter(Boolean);

const cities = (process.env.BOSS_CITIES || "北京,天津,杭州,深圳,郑州")
  .split(/[,，]/)
  .map((item) => item.trim())
  .filter(Boolean);

const maxJobs = Number(process.env.BOSS_MAX_JOBS || "150");
const maxPagesPerSearch = Number(process.env.BOSS_MAX_PAGES || "2");

function buildUrl(query, city, page) {
  const cityCode = cityCodes.get(city);
  if (!cityCode) {
    throw new Error(`Unsupported city: ${city}`);
  }
  const params = new URLSearchParams({
    query,
    city: cityCode,
    page: String(page),
  });
  return `https://www.zhipin.com/web/geek/job?${params.toString()}`;
}

async function requestJson(url, init) {
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${url}`);
  }
  return response.json();
}

class CdpClient {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.events = [];
  }

  async connect() {
    if (typeof WebSocket === "undefined") {
      throw new Error("This Node.js runtime does not expose global WebSocket.");
    }

    this.ws = new WebSocket(this.wsUrl);
    await new Promise((resolve, reject) => {
      this.ws.addEventListener("open", resolve, { once: true });
      this.ws.addEventListener("error", reject, { once: true });
    });

    this.ws.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (message.id && this.pending.has(message.id)) {
        const { resolve, reject } = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) {
          reject(new Error(message.error.message || JSON.stringify(message.error)));
        } else {
          resolve(message.result);
        }
      } else if (message.method) {
        this.events.push(message);
      }
    });
  }

  send(method, params = {}) {
    const id = this.nextId++;
    const payload = JSON.stringify({ id, method, params });
    this.ws.send(payload);
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          reject(new Error(`CDP timeout: ${method}`));
        }
      }, 30000);
    });
  }

  async close() {
    if (this.ws) {
      this.ws.close();
    }
  }
}

async function createPage(url) {
  const encoded = encodeURIComponent(url);
  let target;
  try {
    target = await requestJson(`http://127.0.0.1:${PORT}/json/new?${encoded}`, {
      method: "PUT",
    });
  } catch {
    target = await requestJson(`http://127.0.0.1:${PORT}/json/new?${encoded}`);
  }
  if (!target.webSocketDebuggerUrl) {
    throw new Error("Could not create a debuggable page.");
  }
  const client = new CdpClient(target.webSocketDebuggerUrl);
  await client.connect();
  await client.send("Page.enable");
  await client.send("Runtime.enable");
  return client;
}

async function connectFirstPage() {
  const targets = await requestJson(`http://127.0.0.1:${PORT}/json/list`);
  const target = targets.find((item) => item.type === "page" && item.webSocketDebuggerUrl);
  if (!target) {
    throw new Error("No debuggable browser page was found.");
  }
  const client = new CdpClient(target.webSocketDebuggerUrl);
  await client.connect();
  await client.send("Page.enable");
  await client.send("Runtime.enable");
  return client;
}

async function evaluate(client, expression, awaitPromise = true) {
  const result = await client.send("Runtime.evaluate", {
    expression,
    awaitPromise,
    returnByValue: true,
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text || "Runtime.evaluate failed");
  }
  return result.result?.value;
}

async function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const extractExpression = String.raw`
(() => {
  const clean = (value) => String(value || "").replace(/\s+/g, " ").trim();
  const pick = (root, selectors) => {
    for (const selector of selectors) {
      const node = root.querySelector(selector);
      const text = clean(node && node.innerText);
      if (text) return text;
    }
    return "";
  };
  const hrefOf = (root) => {
    const anchor = root.querySelector("a[href*='/job_detail/'], a[href*='/web/geek/job']");
    if (!anchor) return "";
    try { return new URL(anchor.getAttribute("href"), location.origin).href; } catch { return ""; }
  };
  const selectors = [
    ".job-card-wrapper",
    ".job-list-box li",
    ".search-job-result li",
    "[class*='job-card']",
    "[ka*='search_list']"
  ];
  const cards = [];
  for (const selector of selectors) {
    document.querySelectorAll(selector).forEach((node) => {
      if (!cards.includes(node) && clean(node.innerText).length > 20) {
        cards.push(node);
      }
    });
  }
  return cards.map((card) => {
    const raw = clean(card.innerText);
    const companyName = pick(card, [
      ".company-name",
      "[class*='company-name']",
      ".company-text",
      "[class*='company'] a",
      "[class*='company']"
    ]);
    const position = pick(card, [
      ".job-title",
      "[class*='job-title']",
      ".job-name",
      "[class*='job-name']",
      "a"
    ]);
    const salary = pick(card, [".salary", "[class*='salary']", ".red"]);
    const area = pick(card, [".job-area", "[class*='job-area']", "[class*='location']"]);
    const tags = Array.from(card.querySelectorAll(".tag-list span, [class*='tag'] span, .job-card-footer li"))
      .map((node) => clean(node.innerText))
      .filter(Boolean)
      .slice(0, 12);
    return {
      position,
      companyName,
      salary,
      area,
      tags,
      link: hrefOf(card),
      raw,
    };
  }).filter((item) => item.position || item.companyName || item.raw);
})()
`;

function normalizeItem(item, query, city, page) {
  const raw = item.raw || "";
  const lines = raw.split(/\s+/).filter(Boolean);
  const parsed = parseRawCard(lines, city);
  return {
    source: "BOSS直聘",
    query,
    city,
    page,
    companyName: parsed.companyName || item.companyName || guessCompany(lines),
    position: parsed.position || item.position || lines[0] || "",
    jobLocation: parsed.jobLocation || item.area || city,
    salary: parsed.salary || item.salary || "",
    experience: parsed.experience || "",
    education: parsed.education || "",
    tags: Array.isArray(item.tags) ? item.tags.join(" / ") : "",
    relatedLink: item.link || "",
    raw,
    scrapedAt: new Date().toISOString(),
  };
}

function parseRawCard(lines, city) {
  const areaIndex = lines.findIndex((line) => line.includes("·") && (line.startsWith(city) || /[区县市]·/.test(line)));
  const salaryIndex = lines.findIndex((line, index) =>
    index < (areaIndex >= 0 ? areaIndex : lines.length) && /K|薪|元|￥|¥/.test(line)
  );
  const position = salaryIndex > 0 ? lines.slice(0, salaryIndex).join(" ") : "";
  const salary = salaryIndex >= 0 ? lines[salaryIndex] : "";
  const jobLocation = areaIndex >= 0 ? lines[areaIndex] : "";
  const companyName = areaIndex > 0 ? lines[areaIndex - 1] : "";
  const education = areaIndex > 1 ? lines[areaIndex - 2] : "";
  const experience = areaIndex > 2 ? lines[areaIndex - 3] : "";
  return { position, salary, jobLocation, companyName, education, experience };
}

function guessCompany(lines) {
  const noisy = new Set(["立即沟通", "感兴趣", "刚刚活跃", "今日活跃"]);
  return lines.find((line) => line.length >= 2 && line.length <= 30 && !noisy.has(line)) || "";
}

function keyOf(item) {
  if (item.relatedLink) {
    return item.relatedLink;
  }
  return [item.companyName, item.position, item.jobLocation, item.relatedLink].join("|");
}

function csvEscape(value) {
  const text = String(value ?? "");
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

async function save(items) {
  await fs.mkdir(OUT_DIR, { recursive: true });
  await fs.writeFile(OUT_JSON, JSON.stringify(items, null, 2), "utf8");
  const headers = [
    "source",
    "query",
    "city",
    "companyName",
    "position",
    "jobLocation",
    "salary",
    "experience",
    "education",
    "tags",
    "relatedLink",
    "scrapedAt",
    "raw",
  ];
  const rows = [headers.join(",")].concat(
    items.map((item) => headers.map((header) => csvEscape(item[header])).join(",")),
  );
  await fs.writeFile(OUT_CSV, `${rows.join("\n")}\n`, "utf8");
}

async function loadExisting() {
  try {
    const text = await fs.readFile(OUT_JSON, "utf8");
    const items = JSON.parse(text);
    if (!Array.isArray(items)) {
      return [];
    }
    return items.map((item) => normalizeItem(
      { ...item, link: item.relatedLink, tags: [] },
      item.query || "",
      item.city || "",
      item.page || 1,
    ));
  } catch {
    return [];
  }
}

async function main() {
  await requestJson(`http://127.0.0.1:${PORT}/json/version`).catch((error) => {
    throw new Error(
      `Cannot connect to Edge debug port ${PORT}. Launch it with: powershell -ExecutionPolicy Bypass -File .\\build\\boss-browser.ps1 open -DebugPort ${PORT}\n${error.message}`,
    );
  });

  const seen = new Set();
  const collected = await loadExisting();
  for (const item of collected) {
    seen.add(keyOf(item));
  }
  if (collected.length) {
    console.log(`Loaded ${collected.length} existing jobs`);
  }

  const client = await connectFirstPage();
  try {
    outer:
    for (const city of cities) {
      for (const query of queries) {
        for (let page = 1; page <= maxPagesPerSearch; page += 1) {
          const url = buildUrl(query, city, page);
          console.log(`Opening ${city} / ${query} / page ${page}`);
          try {
            await client.send("Page.navigate", { url });
            await wait(9000);
            await client.send("Page.stopLoading").catch(() => undefined);
            for (let i = 0; i < 3; i += 1) {
              await evaluate(
                client,
                `window.scrollBy(0, Math.max(700, window.innerHeight * 0.8)); document.body.innerText.length`,
                false,
              );
              await wait(1500);
            }
            const pageItems = await evaluate(client, extractExpression);
            const normalized = (pageItems || []).map((item) => normalizeItem(item, query, city, page));
            let added = 0;
            for (const item of normalized) {
              const key = keyOf(item);
              if (!seen.has(key) && (item.companyName || item.position)) {
                seen.add(key);
                collected.push(item);
                added += 1;
              }
            }
            console.log(`  found ${normalized.length}, added ${added}, total ${collected.length}`);
            await save(collected);
            if (collected.length >= maxJobs) break outer;
          } catch (error) {
            console.log(`  skipped: ${error.message}`);
          }
          await wait(2500);
        }
      }
    }
  } finally {
    await client.close();
  }

  await save(collected.slice(0, maxJobs));
  console.log(`Saved ${Math.min(collected.length, maxJobs)} jobs`);
  console.log(OUT_JSON);
  console.log(OUT_CSV);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
