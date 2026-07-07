import fs from "node:fs";
import path from "node:path";

const input = path.join("workspace", "datas", "boss-jobs.json");
const output = path.join("workspace", "datas", "boss-import.sql");

const text = {
  source: "\u6765\u6e90\uff1aBOSS\u76f4\u8058",
  salary: "\u85aa\u8d44\uff1a",
  experience: "\u7ecf\u9a8c\uff1a",
  education: "\u5b66\u5386\uff1a",
  query: "\u5173\u952e\u8bcd\uff1a",
  stateOwned: "\u592e\u56fd\u4f01",
  privateOwned: "\u6c11\u4f01",
  industry: "\u4e92\u8054\u7f51/\u8f6f\u4ef6",
  recruitmentType: "\u793e\u62db",
  target: "\u5f00\u53d1\u76f8\u5173",
  progress: "\u5f85\u6295\u9012",
  deadline: "\u4ee5 BOSS \u76f4\u8058\u9875\u9762\u4e3a\u51c6",
};

const stateOwnedHints = [
  "\u534e\u4e3a",
  "\u4e2d\u79d1",
  "\u795e\u5dde",
  "\u56fd\u4f01",
  "\u77f3\u6cb9",
  "\u94f6\u884c",
];

// AIDEV-NOTE: skip city-only company names
const cityOnlyCompanyNames = new Set([
  "\u5317\u4eac",
  "\u5929\u6d25",
  "\u676d\u5dde",
  "\u6df1\u5733",
  "\u90d1\u5dde",
  "\u4e0a\u6d77",
  "\u5e7f\u5dde",
  "\u5357\u4eac",
  "\u6210\u90fd",
  "\u6b66\u6c49",
  "\u897f\u5b89",
  "\u82cf\u5dde",
  "\u91cd\u5e86",
]);

const today = `${new Date().toISOString().slice(0, 10)} 00:00:00`;
const jobs = JSON.parse(fs.readFileSync(input, "utf8"));

function clean(value, max = 512) {
  return String(value ?? "")
    .replace(/[\uE000-\uF8FF]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function sql(value) {
  return `'${String(value ?? "").replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
}

function companyType(name) {
  return stateOwnedHints.some((hint) => name.includes(hint))
    ? text.stateOwned
    : text.privateOwned;
}

const rows = [];
const seen = new Set();
for (const job of jobs) {
  const companyName = clean(job.companyName, 128);
  const position = clean(job.position, 1024);
  const location = clean(job.jobLocation || job.city, 512);
  const link = clean(job.relatedLink, 512);
  if (
    !companyName ||
    cityOnlyCompanyNames.has(companyName) ||
    !position ||
    !link ||
    seen.has(link)
  ) {
    continue;
  }
  seen.add(link);

  const remarksParts = [
    text.source,
    job.salary ? `${text.salary}${clean(job.salary, 80)}` : "",
    job.experience ? `${text.experience}${clean(job.experience, 80)}` : "",
    job.education ? `${text.education}${clean(job.education, 80)}` : "",
    job.query ? `${text.query}${clean(job.query, 80)}` : "",
  ].filter(Boolean);

  rows.push(
    [
      "INSERT INTO oc_info (draft_id, company_name, company_type, company_industry, job_location, recruitment_type, recruitment_target, position, delivery_progress, last_updated_time, deadline, related_link, job_announcement, internal_referral_code, remarks, state, create_time, update_time)",
      `SELECT 0, ${sql(companyName)}, ${sql(companyType(companyName))}, ${sql(text.industry)}, ${sql(location)}, ${sql(text.recruitmentType)}, ${sql(text.target)}, ${sql(position)}, ${sql(text.progress)}, ${sql(today)}, ${sql(text.deadline)}, ${sql(link)}, ${sql(link)}, '', ${sql(clean(remarksParts.join("\uff1b"), 512))}, 1, NOW(), NOW()`,
      `WHERE NOT EXISTS (SELECT 1 FROM oc_info WHERE related_link = ${sql(link)} AND state <> -1);`,
    ].join("\n"),
  );
}

fs.writeFileSync(output, `${rows.join("\n")}\n`, "utf8");
console.log(`generated ${rows.length} statements -> ${output}`);
