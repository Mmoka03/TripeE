package com.tripee.backend.service.tag;

import com.tripee.backend.repository.tag.TagRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class TagServiceImpl implements TagService {
  private final TagRepository tags;

  public TagServiceImpl(TagRepository tags) {
    this.tags = tags;
  }

  public List<TagRepository.PublicTag> listActive() {
    return tags.listActive();
  }
}
