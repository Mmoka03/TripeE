package com.tripee.backend.service.tag;

import com.tripee.backend.repository.tag.TagRepository.PublicTag;
import java.util.List;

public interface TagService {
  List<PublicTag> listActive();
}
