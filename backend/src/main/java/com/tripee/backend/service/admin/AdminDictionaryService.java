package com.tripee.backend.service.admin;

import com.tripee.backend.model.admin.DictionaryModels.Country;
import com.tripee.backend.model.admin.DictionaryModels.Tag;
import java.util.List;

public interface AdminDictionaryService {
  List<Country> countries();
  List<Tag> tags();
}
