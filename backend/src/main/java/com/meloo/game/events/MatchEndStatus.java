package com.meloo.game.events;

/**
 * Trạng thái kết thúc trận đấu theo mục 2 tài liệu 04-su-kien-va-api-module.md. - FINISHED: hoàn
 * thành đủ số vòng - ABANDONED: trận bỏ dở (chơi đơn quá 30 phút, không tính xếp hạng)
 */
public enum MatchEndStatus {
  FINISHED,
  ABANDONED
}
