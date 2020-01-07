/*
 * Copyright 2019-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.repository.support;

import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;

import reactor.test.StepVerifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.repository.VersionedPerson;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.test.util.MongoTestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

/**
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SimpleReactiveMongoRepositoryVersionedEntityTests {

	@Configuration
	static class Config extends AbstractReactiveMongoConfiguration {

		@Override
		public MongoClient reactiveMongoClient() {
			return MongoTestUtils.reactiveClient();
		}

		@Override
		protected String getDatabaseName() {
			return "database";
		}
	}

	@Autowired //
	private ReactiveMongoTemplate template;

	private MongoEntityInformation<VersionedPerson, String> personEntityInformation;
	private SimpleReactiveMongoRepository<VersionedPerson, String> repository;

	private VersionedPerson sarah;

	@Before
	public void setUp() {

		MongoPersistentEntity entity = template.getConverter().getMappingContext()
				.getRequiredPersistentEntity(VersionedPerson.class);

		personEntityInformation = new MappingMongoEntityInformation(entity);
		repository = new SimpleReactiveMongoRepository<>(personEntityInformation, template);
		repository.deleteAll().as(StepVerifier::create).verifyComplete();

		sarah = repository.save(new VersionedPerson("Sarah", "Connor")).block();
	}

	@Test // DATAMONGO-2195
	public void deleteWithMatchingVersion() {

		repository.delete(sarah).as(StepVerifier::create).verifyComplete();

		template.count(query(where("id").is(sarah.getId())), VersionedPerson.class) //
				.as(StepVerifier::create) //
				.expectNext(0L).verifyComplete();
	}

	@Test // DATAMONGO-2195
	public void deleteWithVersionMismatch() {

		sarah.setVersion(5L);

		repository.delete(sarah).as(StepVerifier::create).verifyError(OptimisticLockingFailureException.class);

		template.count(query(where("id").is(sarah.getId())), VersionedPerson.class) //
				.as(StepVerifier::create) //
				.expectNextCount(1) //
				.verifyComplete();
	}

	@Test // DATAMONGO-2195
	public void deleteNonExisting() {

		repository.delete(new VersionedPerson("T-800")).as(StepVerifier::create)
				.verifyError(OptimisticLockingFailureException.class);
	}

}