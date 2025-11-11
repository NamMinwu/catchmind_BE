package com.catchmind_be.game.response;

import java.util.List;

public record FinishedInfo(
    List<String> orderList,
    int nextIndex,
    boolean isFinished
){
}