import mysql from 'mysql2/promise';
import fs from 'node:fs/promises';
import path from 'node:path';

// 其他域回归产生的通知也归测试账号，删除账号前一并清理。
export async function cleanupImFixtures(ids) {
  if (!ids.length || !process.env.MENG_MYSQL_IM_PASSWORD) return;
  let connection;
  try {
    connection = await mysql.createConnection({ host: '127.0.0.1', port: Number(process.env.MENG_MYSQL_PORT), user: 'meng_im_app', password: process.env.MENG_MYSQL_IM_PASSWORD, database: 'meng_im' });
    const [tables] = await connection.query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='meng_im' AND TABLE_NAME='notification'");
    if (!tables.length) return;
    await new Promise(resolve => setTimeout(resolve, 800));
    const [images] = await connection.query('SELECT file_name,thumb_name FROM message_image WHERE uploader_id IN (?)', [ids]);
    for (const image of images) for (const name of [image.file_name, image.thumb_name]) {
      const directory = path.resolve(process.env.MENG_IM_IMAGE_ROOT), file = path.resolve(directory, name);
      if (!file.startsWith(directory + path.sep)) throw Error('Private fixture image path is outside storage');
      await fs.rm(file, { force: true });
    }
    for (const [table, field] of [['message_image','uploader_id'],['private_message','sender_id'],['conversation_member','user_id'],['notification','receiver_id'],['user_block','user_id'],['im_user_setting','user_id']]) await connection.query(`DELETE FROM ${table} WHERE ${field} IN (?)`, [ids]);
    await connection.query('DELETE FROM conversation WHERE user_low_id IN (?) OR user_high_id IN (?)', [ids, ids]);
  } catch (failure) {
    if (failure.code !== 'ER_BAD_DB_ERROR') throw failure;
  } finally { await connection?.end(); }
}
