package com.catchmind_be.common.utils;

import java.security.SecureRandom;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WordGenerator {

  private static final List<String> DEFAULT_WORDS = List.of(
      "사과", "자동차", "컴퓨터", "강아지", "산", "도시", "책", "나무", "바다", "별"
  );

  private final SecureRandom random = new SecureRandom();

  public String randomWord() {
    return DEFAULT_WORDS.get(random.nextInt(DEFAULT_WORDS.size()));
  }
}

