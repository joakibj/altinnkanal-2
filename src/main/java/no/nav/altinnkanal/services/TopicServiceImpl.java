package no.nav.altinnkanal.services;

import no.nav.altinnkanal.entities.TopicMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class TopicServiceImpl implements TopicService {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public TopicServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TopicMapping getTopicMapping(String serviceCode, String serviceEditionCode) throws Exception {
        return jdbcTemplate.query("SELECT * FROM `topic_mappings` WHERE `service_code`=? AND `service_edition_code`=? AND enabled;",
              new String[] { serviceCode, serviceEditionCode }, (resultSet, rowNum) -> fromResultSet(resultSet)).stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public TopicMapping createTopicMapping(String serviceCode, String serviceEditionCode, String topic, Boolean enabled) {
        jdbcTemplate.update("INSERT INTO `topic_mappings` VALUES (?, ?, ?, ?);", serviceCode,
                serviceEditionCode, topic, enabled);
        return new TopicMapping(serviceCode, serviceEditionCode, topic, enabled);
    }

    private TopicMapping fromResultSet(ResultSet resultSet) throws SQLException {
        String serviceCode = resultSet.getString("service_code");
        String serviceEditionCode = resultSet.getString("service_edition_code");
        String topic = resultSet.getString("topic");
        Boolean enabled = resultSet.getBoolean("enabled");
        return new TopicMapping(serviceCode, serviceEditionCode, topic, enabled);
    }
}
