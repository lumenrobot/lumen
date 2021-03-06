package org.lskk.lumen.persistence.service;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import org.apache.camel.language.Simple;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.lskk.lumen.persistence.LumenPersistenceException;
import org.lskk.lumen.persistence.jpa.YagoLabel;
import org.lskk.lumen.persistence.jpa.YagoType;
import org.lskk.lumen.persistence.jpa.YagoTypeRepository;
import org.lskk.lumen.persistence.neo4j.*;
import org.neo4j.ogm.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.template.Neo4jTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ceefour on 14/02/2016.
 */
@Service
@Transactional
public class FactServiceImpl implements FactService {

    private static final Logger log = LoggerFactory.getLogger(FactServiceImpl.class);

    @Inject
    private ThingRepository thingRepo;
    @Inject
    private ThingLabelRepository thingLabelRepo;
    @Inject
    private YagoTypeRepository yagoTypeRepo;
//    @Inject
    private Neo4jTemplate neo4j;
    @Inject
    private Session session;

    @PostConstruct
    public void init() {
        neo4j = new Neo4jTemplate(session);
    }

    protected static float getMatchingThingConfidence(String upLabel, Locale inLanguage,
                                               List<YagoLabel> labels) {
        if (labels.isEmpty()) {
            return 0f;
        }
        int leastLev = Integer.MAX_VALUE;
        YagoLabel bestLabel = null;
        for (final YagoLabel label : labels) {
            final int lev = StringUtils.getLevenshteinDistance(upLabel, label.getValue());
            if (bestLabel == null || lev < leastLev) {
                bestLabel = label;
                leastLev = lev;
            }
        }
        final double inverseLevenshteinMultiplier = Math.max((-Math.exp(0.25 * leastLev) + 11d) / 10d, 0d);
        final double languageMultiplier;
        if (bestLabel.getInLanguage() != null) {
            final Locale labelLang = Locale.forLanguageTag(bestLabel.getInLanguage());
            if (inLanguage.equals(labelLang)) {
                languageMultiplier = 1d;
               } else if (inLanguage.getLanguage().equals(labelLang.getLanguage())) {
                languageMultiplier = 0.9d;
            } else {
                languageMultiplier = 0.5d;
            }
        } else {
            languageMultiplier = 0.5d;
        }
        final float confidence = (float) (inverseLevenshteinMultiplier * languageMultiplier);
        return confidence;
    }

    /**
     * Confidence is calculated as follows:
     *
     * inverseLevenshteinMultiplier = max( (-e^(0.25 * levenshtein[10])+11)/10, 0)
     * languageMultiplier = {matches: 1, partial match: 0.9, not match/unknown: 0.5}
     *
     * @param upLabel Label to match, free-form. Fuzzy string matching will also be attempted,
     *                which will be reflected in {@link MatchingThing#getConfidence()}.
     * @param inLanguage Language of the provided label.
     * @param contexts Contexts of the match. Key is node name, value is non-normalized confidence [0..1].
     * @return
     */
    @Override
    public MatchingThings match(String upLabel, Locale inLanguage, Map<String, Float> contexts) {
        final List<MatchingThing> results = new ArrayList<>();

        // TODO: use metaphone

        final List<Thing> neo4jThings = thingRepo.findAllByPrefLabelOrIsPreferredMeaningOf(upLabel);
        neo4jThings.stream().map(it -> {
            final ArrayList<YagoLabel> labels = new ArrayList<>();
            if (null != it.getPrefLabel()) {
                labels.add(new YagoLabel(it.getPrefLabel(), it.getPrefLabelLang()));
            }
            final float confidence = getMatchingThingConfidence(upLabel, inLanguage, labels);
            return new MatchingThing(it, confidence);
        }).forEach(results::add);

        // find matches from YagoType
        final List<YagoType> yagoTypes = yagoTypeRepo.findAllByPrefLabelOrIsPreferredMeaningOfEager(upLabel);
        yagoTypes.stream().map(it -> {
            final ArrayList<YagoLabel> labels = new ArrayList<>();
            if (null != it.getPrefLabel()) {
                labels.add(new YagoLabel(it.getPrefLabel(), Locale.US.toLanguageTag()));
            }
            if (null != it.getIsPreferredMeaningOf()) {
                labels.add(new YagoLabel(it.getIsPreferredMeaningOf(), Locale.US.toLanguageTag()));
            }
            final float confidence = getMatchingThingConfidence(upLabel, inLanguage, labels);
            return new MatchingThing(it.toThingFull(), confidence);
        }).forEach(results::add);

        // Sort results based on confidence
        final List<MatchingThing> sortedResults = Ordering.natural().immutableSortedCopy(results)
                .stream().limit(MAX_RESULTS).collect(Collectors.toList());
        log.info("match() returned {} matches using ({}, {}, {}) => {}",
                sortedResults.size(), upLabel, inLanguage, contexts, sortedResults);

        return new MatchingThings(sortedResults);
    }

    @Override
    public Thing describeThing(String nodeName) {
        Thing result = null;
        final Thing varThing = thingRepo.findOneByPartitionAndNn(PartitionKey.lumen_var, nodeName);
        if (varThing != null) {
            result = varThing;
        } else {
            final YagoType yagoType = yagoTypeRepo.findOneByNn(nodeName);
            if (null != yagoType) {
                result = yagoType.toThingFull();
            }
        }
        if (null == result) {
            throw new LumenPersistenceException(String.format("Cannot find thing '%s'", nodeName));
        }
        return result;
    }

    @Override
    public Thing assertThing(String nodeName, String upLabel, Locale inLanguage, boolean isPrefLabel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Thing unassertThing(String nodeName) {
        return null;
    }

    @Override
    public Statement assertPropertyToThing(String nodeName, String property, String objectNodeName, float[] truthValue, DateTime assertionTime, String asserterNodeName) {
        final String matchCypher =
                "MATCH (s:owl_Thing {nn: {nn}}) WHERE s._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MATCH (p:owl_Thing {nn: {property}}) WHERE p._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MATCH (o:owl_Thing {nn: {objectNodeName}}) WHERE o._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MATCH (p) <-[:rdf_predicate]- (statement:rdf_Statement {_partition: {partitionKey}}) -[:rdf_subject]-> (s),\n" +
                "                              (statement) -[:rdf_object]-> (o)\n" +
                "SET statement.tv={tv}" +
                "RETURN statement";
        final String createCypher =
                "MATCH (s:owl_Thing {nn: {nn}}) WHERE s._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MATCH (p:owl_Thing {nn: {property}}) WHERE p._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MATCH (o:owl_Thing {nn: {objectNodeName}}) WHERE o._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "CREATE (p) <-[:rdf_predicate]- (statement:rdf_Statement {_partition: {partitionKey}}) -[:rdf_subject]-> (s)," +
                "                               (statement) -[:rdf_object]-> (o)\n" +
                "SET statement.tv={tv}\n" +
                "RETURN statement";
        final ImmutableMap<String, Object> params = ImmutableMap.<String, Object>builder()
                .put("partitionKey", PartitionKey.lumen_var)
                .put("nn", nodeName)
                .put("property", property)
                .put("objectNodeName", objectNodeName)
                .put("tv", truthValue)
                .build();
        log.trace("assertPropertyToThing existing {} using {}", params, matchCypher);
        Statement statement = neo4j.queryForObject(Statement.class, matchCypher, params);
        if (null == statement) {
            statement = neo4j.queryForObject(Statement.class, createCypher, params);
            Preconditions.checkNotNull(statement, "Cannot assert rdf_Statement because either subject '%s' or property '%s' or object '%s' not found",
                    nodeName, property, objectNodeName);
        }
        return statement;
    }

    @Override
    public Literal assertPropertyToLiteral(@Simple("body.nodeName") String nodeName, @Simple("body.property") String property,
                                           @Simple("body.objectType") String objectType, @Simple("body.object") Object object, @Simple("body.truthValue") float[] truthValue,
                                           @Simple("body.assertionTime") DateTime assertionTime, @Simple("body.asserterNodeName") String asserterNodeName) {
        //final String relationship = property.replace(':', '_');
        final String cypher = "MATCH (n:owl_Thing {nn: {nn}}) WHERE n._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MATCH (p:rdf_Property {nn: {property}}) WHERE p._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MERGE (n) <-[:rdf_subject]- (literal:rdfs_Literal {t: {type}, v: {value}, _partition: {partitionKey}}) -[:rdf_predicate]-> (p)\n" +
                "SET literal.tv={tv}\n" +
                "RETURN literal";
        final ImmutableMap<String, Object> params = ImmutableMap.<String, Object>builder()
                .put("partitionKey", PartitionKey.lumen_var)
                .put("nn", nodeName)
                .put("property", property)
                .put("type", objectType)
                .put("value", object)
                .put("tv", truthValue)
                .build();
        log.trace("assertPropertyToLiteral {} using {}", params, cypher);
        final Literal literal = neo4j.queryForObject(Literal.class, cypher, params);
        Preconditions.checkNotNull(literal, "Cannot assert literal \"%s\":%s because either thing '%s' or property '%s' not found",
                object, objectType, nodeName, property);
        return literal;
    }

    @Override
    public ThingLabel assertLabel(@Simple("body.nodeName") String nodeName, @Simple("body.property") String property, @Simple("body.label") String label, String inLanguage, @Simple("body.truthValue") float[] truthValue, @Simple("body.assertionTime") DateTime assertionTime, @Simple("body.asserterNodeName") String asserterNodeName) {
        final String metaphone = ThingLabel.METAPHONE.encode(label);

        final String relationship = property.replace(':', '_');
        final String cypher = "MATCH (n:owl_Thing {nn: {nn}}) WHERE n._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
                "MERGE (n) -[:" + relationship + "]-> (label:lumen_Label {l: {inLanguage}, v: {value}, _partition: {partitionKey}})\n" +
                "SET label.tv={tv}, label.m={metaphone}\n" +
                "RETURN label";

//        @Query("MATCH (n:owl_Thing {nn: {nn}}) WHERE n._partition IN ['lumen_yago', 'lumen_platform', 'lumen_var']\n" +
//                "MERGE (n) -[:{property}]-> (label:lumen_Label {l: {inLanguage}, v: {value}, _partition: {partitionKey}})\n" +
//                "SET label.tv={tv}, label.m={metaphone}\n" +
//                "RETURN label")
//        ThingLabel assertLabel(@Param("partitionKey") PartitionKey partitionKey,
//                @Param("nn") String nn,
//                @Param("property") String property,
//                @Param("inLanguage") String inLanguage,
//                @Param("value") String value,
//        @Param("tv") float[] tv,
//        @Param("metaphone") String metaphone);

//        return thingLabelRepo.assertLabel(PartitionKey.lumen_var, nodeName, relationship, inLanguage, label, truthValue, metaphone);

        final ImmutableMap<String, Object> params = ImmutableMap.<String, Object>builder()
                .put("partitionKey", PartitionKey.lumen_var)
                .put("nn", nodeName)
                .put("property", property)
                .put("inLanguage", inLanguage)
                .put("value", label)
                .put("tv", truthValue)
                .put("metaphone", metaphone)
                .build();
        log.trace("assertLabel {} using {}", params, cypher);
        final ThingLabel labelNode = neo4j.queryForObject(ThingLabel.class, cypher, params);
        Preconditions.checkNotNull(labelNode, "Cannot assert %s \"%s\"@%s because thing '%s' not found",
                property, label, inLanguage, nodeName);
        return labelNode;
    }

    @Override
    public Fact getProperty(@Simple("body.nodeName") String nodeName, @Simple("body.property") String property) {
        final Literal literal =
                neo4j.queryForObject(Literal.class,
                        "MATCH (s:schema_Thing {nn: {nodeName}}) <-[:rdf_subject]- (l:rdfs_Literal) -[:rdf_predicate]-> (p:rdf_Property {nn: {property}})" +
                        " WHERE s._partition IN {partitions} AND p._partition IN {partitions}" +
                        " RETURN l LIMIT 1",
                ImmutableMap.of("nodeName", nodeName, "property", property, "partitions", new String[]{PartitionKey.lumen_yago.name(), PartitionKey.lumen_var.name()}));
        if (literal != null) {
//            final Literal literal = (Literal) result.get(0).get("l");
//            final Thing subject = (Thing) result.get(0).get("s");
//            final SemanticProperty predicate = (SemanticProperty) result.get(0).get("p");
            final Thing subject = literal.getSubject();
//            final SemanticProperty predicate = literal.getPredicate();
            final Fact fact = new Fact();
            fact.setSubject(subject);
            fact.setProperty(property);
            if ("xsd:string".equals(literal.getType())) {
                fact.setKind(FactKind.STRING);
                fact.setObjectAsString((String) literal.getValue());
            } else if ("xs:date".equals(literal.getType())) {
                fact.setKind(FactKind.LOCALDATE);
                fact.setObjectAsLocalDate(new LocalDate(literal.getValue()));
            } else {
                throw new LumenPersistenceException(String.format("Unknown Literal type: %s with value '%s' for node %s",
                        literal.getType(), literal.getValue(), literal.getGid()));
            }
            log.info("getProperty {} {} returns {}", nodeName, property, fact);
            return fact;
        } else {
            log.info("getProperty {} {} returns null", nodeName, property);
            return null;
        }
    }
}
