package com.gentics.mesh.core.data.model;

import static com.gentics.mesh.util.TinkerpopUtils.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.core.data.model.tinkerpop.Language;
import com.gentics.mesh.core.data.service.LanguageService;
import com.gentics.mesh.test.AbstractDBTest;

public class LanguageTest extends AbstractDBTest {

	@Autowired
	private LanguageService languageService;

	@Before
	public void setup() throws Exception {
		setupData();
	}

	@Test
	public void testCreation() {
		final String languageTag = "tlh";
		final String languageName = "klingon";
		Language lang = languageService.create(languageName, languageTag);
//		try (Transaction tx = graphDb.beginTx()) {
			lang = languageService.save(lang);
//			tx.success();
//		}
		lang = languageService.findOne(lang.getId());
		assertNotNull(lang);
		assertEquals(languageName, lang.getName());

		assertNotNull(languageService.findByLanguageTag(languageTag));
	}

	@Test
	public void testLanguageRoot() {
		int nLanguagesBefore = count(languageService.findRoot().getLanguages());

//		try (Transaction tx = graphDb.beginTx()) {
			final String languageName = "klingon";
			final String languageTag = "tlh";
			Language lang = languageService.create(languageName, languageTag);
			languageService.save(lang);
//			tx.success();
//		}

		int nLanguagesAfter = count(languageService.findRoot().getLanguages());
		assertEquals(nLanguagesBefore + 1, nLanguagesAfter);

	}

}
