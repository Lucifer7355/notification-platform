package com.notificationplatform.persistence;

import com.notificationplatform.domain.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderInboxJpaRepository extends JpaRepository<ProviderInboxEntity, Long> {

    List<ProviderInboxEntity> findByChannelOrderByReceivedAtDesc(ChannelType channel);
}
