package com.vicopol.academy.aws.domain.repository;

import com.vicopol.academy.aws.domain.entity.File;

public interface DomainRepository {
    Long insert(File file);
}
