package com.tripee.backend.service.admin;

import com.tripee.backend.model.admin.DictionaryModels.Country;
import com.tripee.backend.model.admin.DictionaryModels.Tag;
import com.tripee.backend.repository.admin.AdminDictionaryRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class AdminDictionaryServiceImpl implements AdminDictionaryService {
  private final AdminDictionaryRepository dictionaries;

  public AdminDictionaryServiceImpl(AdminDictionaryRepository dictionaries) {
    this.dictionaries = dictionaries;
  }

  public List<Country> countries() {
    return dictionaries.countries();
  }

  public List<Tag> tags() {
    return dictionaries.tags();
  }
}
