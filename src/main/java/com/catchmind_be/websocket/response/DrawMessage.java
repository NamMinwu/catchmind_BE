package com.catchmind_be.websocket.response;

public record DrawMessage (
  String playerId,
  double fromX,
  double fromY,
  double toX,
  double toY,
  String color,
  double lineWidth
){

}
