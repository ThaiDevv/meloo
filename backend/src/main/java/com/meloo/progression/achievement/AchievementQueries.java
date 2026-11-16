package com.meloo.progression.achievement;

import com.meloo.generated.model.AchievementItemDto;
import com.meloo.generated.model.AchievementListResponseDto;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AchievementQueries {
  private final JdbcTemplate jdbc;

  public AchievementQueries(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public AchievementListResponseDto list(UUID user) {
    var items =
        jdbc.query(
            """
        select a.*, u.unlocked_at from achievement a left join user_achievement u
          on u.achievement_code = a.code and u.user_id = ? order by a.sort_order, a.code
        """,
            (rs, row) -> {
              var at = rs.getTimestamp("unlocked_at");
              return new AchievementItemDto(
                      rs.getString("code"),
                      rs.getString("name_vi"),
                      rs.getString("name_en"),
                      rs.getString("description_vi"),
                      rs.getString("description_en"),
                      rs.getInt("sort_order"),
                      at != null)
                  .unlockedAt(at == null ? null : at.toInstant().atOffset(ZoneOffset.UTC));
            },
            user);
    return new AchievementListResponseDto(
        (int) items.stream().filter(i -> i.getUnlocked()).count(), items.size(), items);
  }
}
