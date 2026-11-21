package com.tripee.backend.repository.tag;

import java.util.List;

public interface TagRepository {
  List<PublicTag> listActive();
  record PublicTag(String id, String code, String labelKo, String labelEn, boolean isActive) {}
}
