package com.meloo.room.lobby;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Sinh và kiểm tra mã phòng 6 ký tự theo quy tắc BR-11.
 *
 * <p>Mã gồm 6 ký tự chữ in hoa và chữ số, loại trừ các ký tự dễ nhầm lẫn: 0, O, 1, I, L. Tập ký tự
 * hợp lệ là: 2, 3, 4, 5, 6, 7, 8, 9, A, B, C, D, E, F, G, H, J, K, M, N, P, Q, R, S, T, U, V, W, X,
 * Y, Z (khớp regex OpenAPI {@code ^[A-HJ-NP-Z2-9]{6}$}).
 */
@Component
public class RoomCodeGenerator {

  public static final String VALID_CHARACTERS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  public static final int CODE_LENGTH = 6;
  public static final String CODE_REGEX = "^[A-HJ-NP-Z2-9]{6}$";

  private final Random random;

  public RoomCodeGenerator() {
    this(new SecureRandom());
  }

  RoomCodeGenerator(Random random) {
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  /**
   * Sinh mã phòng 6 ký tự ngẫu nhiên duy nhất dựa trên điều kiện kiểm tra tồn tại.
   *
   * @param isUnique kiểm tra xem mã đã tồn tại chưa (trả về true nếu mã hợp lệ chưa tồn tại)
   * @return mã phòng 6 ký tự hợp lệ
   */
  public String generateUniqueCode(Predicate<String> isUnique) {
    for (int attempt = 0; attempt < 100; attempt++) {
      String code = generateCode();
      if (isUnique.test(code)) {
        return code;
      }
    }
    throw new IllegalStateException("Failed to generate a unique room code after 100 attempts");
  }

  /** Sinh một mã phòng 6 ký tự ngẫu nhiên. */
  public String generateCode() {
    char[] chars = new char[CODE_LENGTH];
    for (int i = 0; i < CODE_LENGTH; i++) {
      chars[i] = VALID_CHARACTERS.charAt(random.nextInt(VALID_CHARACTERS.length()));
    }
    return new String(chars);
  }

  /** Kiểm tra chuỗi có phải mã phòng hợp lệ theo BR-11 không. */
  public boolean isValidCode(String code) {
    if (code == null || code.length() != CODE_LENGTH) {
      return false;
    }
    for (int i = 0; i < CODE_LENGTH; i++) {
      if (VALID_CHARACTERS.indexOf(code.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }
}
