package com.pixierge.api;

import com.querydsl.sql.PostgreSQLTemplates;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.spring.SpringConnectionProvider;
import com.querydsl.sql.spring.SpringExceptionTranslator;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {

  @Bean
  SQLQueryFactory sqlQueryFactory(DataSource dataSource) {
    SQLTemplates templates = PostgreSQLTemplates.builder().build();
    com.querydsl.sql.Configuration configuration = new com.querydsl.sql.Configuration(templates);
    configuration.setExceptionTranslator(new SpringExceptionTranslator());

    return new SQLQueryFactory(configuration, new SpringConnectionProvider(dataSource));
  }
}
