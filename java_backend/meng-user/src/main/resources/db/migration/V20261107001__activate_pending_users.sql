-- 注册改为免审核：已在等待审核的账号一并改为正常，被拒绝的不动
UPDATE user SET status = 'NORMAL' WHERE status = 'PENDING' AND delete_state = 0;
