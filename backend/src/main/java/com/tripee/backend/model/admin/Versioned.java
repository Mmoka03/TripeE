package com.tripee.backend.model.admin;

/** 현재 데이터와 조건부 변경에 사용하는 불투명 버전 값. */
public record Versioned<T>(T value, String version) {}
