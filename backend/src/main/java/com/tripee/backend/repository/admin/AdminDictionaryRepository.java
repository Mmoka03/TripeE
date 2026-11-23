package com.tripee.backend.repository.admin;

import com.tripee.backend.model.admin.DictionaryModels.Country;
import com.tripee.backend.model.admin.DictionaryModels.Tag;
import java.util.List;

public interface AdminDictionaryRepository {
  List<Country> countries();
  List<Tag> tags();
}
