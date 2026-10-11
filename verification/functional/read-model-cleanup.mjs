export async function cleanupPostReadModels(redis, ids) {
  if (!ids.length) return;
  // Pending index jobs reread MySQL; let completed fixture deletions reach the writer.
  await new Promise((resolve) => setTimeout(resolve, 1000));
  const base = `http://127.0.0.1:${process.env.MENG_ES_PORT}`;
  const headers = {
    Authorization:
      "Basic " +
      Buffer.from(
        `meng_content_app:${process.env.MENG_ES_CONTENT_PASSWORD}`,
      ).toString("base64"),
  };
  for (const id of ids) {
    const response = await fetch(`${base}/meng_post/_doc/${id}`, {
      method: "DELETE",
      headers,
      signal: AbortSignal.timeout(3000),
    });
    if (!response.ok && response.status !== 404)
      throw Error(`Cannot clean fixture index document: ${response.status}`);
  }
  const members = ids.map((id) => String(id).padStart(19, "0"));
  const batch = redis.multi();
  for (const color of ["blue", "green"])
    for (const category of [
      "ALL",
      "ANIME",
      "CREATION",
      "GAME",
      "TECH",
      "DAILY",
    ])
      batch.zRem(`meng:content:hot:${color}:${category}`, members);
  await batch.exec();
}
