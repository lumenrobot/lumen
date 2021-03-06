# Lumen Persistence

Persistence module for Lumen Social Robot.

## Preparation

1. Ensure PostgreSQL 9.5+ is installed and running. If not, install from http://www.postgresql.org/ .
2. In PgAdmin, create database `lumen_lumen_dev` using `UTF-8` encoding.
3. Download template PostgreSQL database snapshot from https://drive.google.com/file/d/0B9dx38a6NVxKblMzTGhydWFWNXM/view?usp=sharing .
4. In PgAdmin, restore from that PostgreSQL template database snapshot.
5. Ensure Neo4j 2.3+ is running. If not, install from http://neo4j.org/ .
6. Download template Neo4j database snapshot from https://drive.google.com/file/d/0B9dx38a6NVxKTHIwOXJqanh4RDA/view?usp=sharing .
7. Extract that template Neo4j database snapshot to `C:\Users\<username>\Neo4j\Documents` (will create `default.graphdb` subfolder).
8. Copy `config/application.dev.properties` to `config/application.properties`
9. Edit `config/application.properties`
10. Run

## Running from Command Line

To run from command line, first you have to prepare its dependencies, then build it.

1. Build/deploy `lumen-sdk/java`
2. Build `lumen-persistence` plus `dependency:copy-dependencies`:

        mvn -DskipTests install dependency:copy-dependencies

## Overview

Persistence handles the following general categories, each one is handled differently:

1. **Journal**. Records and provides daily activities of each robot. This is *not* the system log.
   Each journal is local to a robot.
   
2. **Facts**. Records information related to people, objects, and other robots (collectively called _entities_);
   and their resource and literal properties (collectively called _facts_).
   
   Facts are scoped:
   
   1. _global_ facts, applies to all robots
   2. _group_ facts, applies to some robots joining a specific group
   3. _instance_ facts, applies to an individual robot
   
   Writes to _group_ and _instance_ facts are normally also written to audit history,
   in order to provide insight who, when, and why was the fact recorded.
   
3. **Knowledge**. Records semantic meanings and inferences.

## Journal Persistence

Stored in Neo4j from daily activities of each robot. This is *not* the system log. Each journal is local to a robot.

## Fact Persistence

Stored in Neo4j. All facts are indexed for quick lookup.
Fact persistence also have quick graph traversal performance, because backed by a graph database.

### Sample storable facts

For person B.J. Habibie (taken from https://gate.d5.mpi-inf.mpg.de/webyago3spotlx/Browser?entity=%3CB._J._Habibie%3E):

| Fact ID                | Subject             | Property      | Object                                 | Time       | Location  | Keywords                      |
|------------------------|---------------------|---------------|----------------------------------------|------------|-----------|-------------------------------|
| id_1xidad2_1xk_uv85ns  | <yago:B.J._Habibie> | wasBornOnDate | 1936-06-25                             | 1936-06-25 | Pare-pare | Hasri Ainun Besari, Gorontalo |
|                        | <yago:B.J._Habibie> | label         | "Bacharuddin Jusuf Habibie"@ind        |            |           |                               |
| id_1xidad2_1sz_1iw0bpy | <yago:B.J._Habibie> | prefLabel     | "B.J. Habibie"@eng                     |            |           |                               |
| id_1xidad2_10x_1m2huro | <yago:B.J._Habibie> | graduatedFrom | <yago:Bandung_Institute_of_Technology> |            | Bandung   |                               |
| id_1xidad2_16x_n6kx1s  | <yago:B.J._Habibie> | isMarriedTo   | <yago:Hasri_Ainun_Habibie>             |            |           |                               |
| id_1xidad2_p3m_zkjp59  | <yago:B.J._Habibie> | hasGender     | male                                   |            |           |                               |

### Sample Data

Complete list of properties are available in `Dropbox/Lumen/LumenSchema.xlsx`, here a few samples are provided.

#### yago:wasBornOnDate

    MERGE (hendy:owl_Thing {nn: 'lumen:Hendy_Irawan', _partition: 'lumen_var'}) SET hendy.prefLabel='Hendy Irawan'
    MERGE (wasBornOnDate:rdf_Property {nn: 'yago:wasBornOnDate', _partition: 'lumen_yago'}) SET wasBornOnDate.label='was born on date'
    MERGE (wasBornOnDate) <-[:rdf_predicate]- (literal:rdfs_Literal {t: 'xs:date', v: '1983-12-14', _partition: 'lumen_var'}) -[:rdf_subject]-> (hendy)
    RETURN wasBornOnDate, hendy, literal

#### rdfs:label

    MATCH (hendy:owl_Thing {nn: 'lumen:Hendy_Irawan'}) WHERE hendy._partition IN ['lumen_yago', 'lumen_common', 'lumen_var']
    MERGE (hendy) -[label:rdfs_label]-> (:lumen_Label {l: 'id-ID', v: 'Hendy Irawan', _partition: 'lumen_var'})
    SET hendy.tv=[1.0, 1.0], hendy.m='HNTRWN' 
    RETURN label

### Sample Data (obsolete)

Yago2s DB import12 must be imported first, otherwise resources won't `MATCH`.

    MATCH (city:Resource {href: 'yago:Bandung'})
    MATCH (grad:Resource {href: 'yago:Bandung_Institute_of_Technology'})
    MATCH (organization:Resource {href: 'yago:wordnet_organization_108008335'})
    WITH city, grad, organization
    MERGE (subj:Resource {href: 'lumen:Lumen_LSKK', prefLabel: 'Lumen LSKK', isPreferredMeaningOf: 'Lumen LSKK'})
    MERGE (subj) -[:rdf_type]-> (organization)
    MERGE (subj) -[:yago_isAffiliatedTo]-> (grad)
    MERGE (subj) -[:yago_isLocatedIn]-> (city)
    RETURN subj;

        MATCH (orgn:Resource {href: 'lumen:Lumen_LSKK'})
        MATCH (city:Resource {href: 'yago:Bandung'})
        MATCH (grad:Resource {href: 'yago:Bandung_Institute_of_Technology'})
        MATCH (person:Resource {href: 'yago:wordnet_person_100007846'})
        MATCH (organization:Resource {href: 'yago:wordnet_organization_108008335'})
        MATCH (male:Resource {href: 'yago:male'})
        MATCH (female:Resource {href: 'yago:female'})
        MATCH (living:Resource {href: 'yago:wikicategory_Living_people'})
        MATCH (alumnus:Resource {href: 'yago:wordnet_alumnus_109786338'})
        WITH orgn, city, grad, person, organization, male, female, living, alumnus
            
        MERGE (subj:Resource {href: 'lumen:Nurfitri_Anbarsanti', prefLabel:'Nurfitri Anbarsanti', isPreferredMeaningOf: 'Nurfitri Anbarsanti'})
        CREATE (frontFace:ImageObject {contentType: 'image/jpeg', name: 'fitri01.jpg', contentUrl: ''})
        MERGE (subj) -[:yago_isAffiliatedTo]-> (orgn)
        MERGE (subj) -[:yago_hasGender]-> (female)
        MERGE (subj) -[:rdf_type]-> (person)
        MERGE (subj) -[:rdf_type]-> (living)
        MERGE (subj) -[:rdf_type]-> (alumnus)
        MERGE (subj) -[:yago_wasBornIn]-> (city)
        MERGE (subj) -[:yago_graduatedFrom]-> (grad)
        MERGE (subj) -[:lumen_hasFrontFace]-> (frontFace)
        MERGE (subj) -[:vcard_hasPhoto]-> (frontFace)
        MERGE (subj) -[:vcard_sound]-> (:AudioObject {contentType: 'audio/ogg', name: 'fitri01.ogg', contentUrl: ''})
        MERGE (subj) -[:vcard_sound]-> (:AudioObject {contentType: 'audio/ogg', name: 'fitri02.ogg', contentUrl: ''})
        MERGE (subj) -[:lumen_hasStudy]-> (:Literal {v: 'S2 Teknik Komputer', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasModule]-> (:Literal {v: 'gesture recognition', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasStudentNumber]-> (:Literal {v: '23212071', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasPaper]-> (:Literal {v: 'Dance Modelling, Learning and Recognition System of Aceh Traditional Dance based on Hidden Markov Model http://www.academia.edu/8972445/23212071_-_Paper_6_pages', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasPaper]-> (:Literal {v: 'A New RTL Design Approach for a DCT/IDCT-Based Image Compression Architecture using the mCBE Algorithm http://journal.itb.ac.id/index.php?li=article_detail&id=1072', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasWhatsApp]-> (:Literal {v: '+6281322299130', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasBBM]-> (:Literal {v: '', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasTwitter]-> (:Literal {v: '', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasFacebook]-> (:Literal {v: 'anbarsanti', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasGitHub]-> (:Literal {v: 'anbarsanti', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasBitBucket]-> (:Literal {v: '', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasLinkedIn]-> (:Literal {v: 'anbarsanti', t: 'xsd:string'})
        MERGE (subj) -[:lumen_hasAcademia]-> (:Literal {v: 'NurfitriAnbarsanti', t: 'xsd:string'})
        MERGE (subj) -[:vcard_hasAddress]-> (:Literal {v: 'Jalan Uranus Blok C3 no.11 Komp. Margahayu Raya Barat, Bandung', t: 'xsd:string'})
        MERGE (subj) -[:vcard_hasEmail]-> (:Literal {v: 'anbarsanti@yahoo.com', t: 'xsd:string'})
        MERGE (subj) -[:vcard_hasTelephone]-> (:Literal {v: '+6281322299130', t: 'xsd:string'})
        MERGE (subj) -[:vcard_hasRelativeAddress]-> (:Literal {v: 'Jalan Uranus Blok C3 no.11 Komp. Margahayu Raya Barat, Bandung', t: 'xsd:string'})
        MERGE (subj) -[:vcard_hasRelativeName]-> (:Literal {v: 'Wulan', t: 'xsd:string'})
        MERGE (subj) -[:vcard_hasRelativeTelephone]-> (:Literal {v: '+6281394094866', t: 'xsd:string'})

        RETURN subj;

### Delete and/or Replace Relationship

    MATCH (:Resource {href: 'lumen:Budhi_Yulianto'}) -[r:yago_graduatedFrom]-> ()
    DELETE r;

    MATCH (subj:Resource {href: 'lumen:Budhi_Yulianto'}),
        (grad:Resource {href: 'yago:Bandung_Institute_of_Technology'})
    MERGE (subj) -[:lumen_hasStudy]-> (grad);

### Sample Data (old)

    CREATE (wordnet_person_100007846:Class {uri: 'http://yago-knowledge.org/resource/wordnet_person_100007846', label: 'person'})
    CREATE (wordnet_city_108524735:Class {uri: 'http://yago-knowledge.org/resource/wordnet_city_108524735', label: 'city'})
    CREATE (Budhi_Yulianto:wordnet_person_100007846 {uri: 'http://lumen.lskk.ee.itb.ac.id/resource/Budhi_Yulianto', label: 'Budhi Yulianto'})
    CREATE (Budhi_Yulianto_label:Text {value: 'Budhi Yulianto', language: 'ind'})
    CREATE (Bandung:wordnet_city_108524735 {uri: 'http://yago-knowledge.org/resource/Bandung', label: 'Bandung'})
    CREATE (Bandung_label:Text {value: 'Bandung', language: 'ind'})
    CREATE (Bandung_label2:Text {value: 'Parijs van Java', language: 'ind'})

    CREATE (Budhi_Yulianto) -[:type]-> (wordnet_person_100007846)
    CREATE (Budhi_Yulianto) -[:label]-> (Budhi_Yulianto_label)
    CREATE (Bandung) -[:type]-> (wordnet_city_108524735)
    CREATE (Bandung) -[:label]-> (Bandung_label)
    CREATE (Bandung) -[:label]-> (Bandung_label2)
    CREATE (Budhi_Yulianto) -[:wasBornIn]-> (Bandung)
    
    RETURN Budhi_Yulianto

### To Delete All
    
    /* to delete all
    MATCH (a)-[r]-(b) DELETE r
    MATCH n DELETE n
    */

### Sample to Create Relationship from Existing Nodes
    
    /* sample to create from existing nodes
    MATCH Person WHERE Person.uri = 'http://yago-knowledge.org/resource/Person'
    MATCH Budhi_Yulianto WHERE Budhi_Yulianto.uri = 'http://lumen.lskk.ee.itb.ac.id/resource/Budhi_Yulianto'
    CREATE (Budhi_Yulianto)-[:instanceOf]->(Person) 
    RETURN Budhi_Yulianto
    */

### Facts as Relationships

A simple `Fact` is direct relationship from a subject node to object node.
A `Fact` relationship has a stable referenceable `id` (the Fact ID, usually a UUID), and may contain any number of additional
metadata (temporal and spatial) as Neo4j properties.

### Useful Queries

Get a `Resource` by label:

    OPTIONAL MATCH (e1:Resource {prefLabel: 'B. J. Habibie'})
    OPTIONAL MATCH (e2:Resource {isPreferredMeaningOf: 'B. J. Habibie'})
    OPTIONAL MATCH (e3:Resource) -[:rdfs_label]-> (l:Label {v:'B. J. Habibie'})
    RETURN coalesce(e1, e2, e3);

    OPTIONAL MATCH (e1:Resource {prefLabel: 'Hasri Ainun Habibie'})
    OPTIONAL MATCH (e2:Resource {isPreferredMeaningOf: 'Hasri Ainun Habibie'})
    OPTIONAL MATCH (e3:Resource) -[:rdfs_label]-> (l:Label {v:'Hasri Ainun Habibie'})
    RETURN coalesce(e1, e2, e3);

Get a `Class` by `isPreferredMeaningOf` and all its supertypes:

    MATCH (c: Resource {isPreferredMeaningOf: 'person'}), (c) -[:rdfs_subClassOf*]-> (t)
    RETURN c, t;

Get a `Class` by `prefLabel` and all its supertypes:

    MATCH (c: Resource {prefLabel: 'person'}), (c) -[:rdfs_subClassOf*]-> (t)
    RETURN c, t;

    MATCH (c: Resource {prefLabel: 'alumnus'}), (c) -[:rdfs_subClassOf*]-> (t)
    RETURN c, t;

Get a `Class` by all labels, return it and all its supertypes:

    OPTIONAL MATCH (e1:Resource {prefLabel: 'person'})
    OPTIONAL MATCH (e2:Resource {isPreferredMeaningOf: 'person'})
    OPTIONAL MATCH (e3:Resource) -[:rdfs_label]-> (l:Label {v:'person'})
    WITH coalesce(e1, e2, e3) AS c
    MATCH (c) -[:rdfs_subClassOf*]-> (t)
    RETURN c, t;

Get a `Resource` by label and all its types and supertypes:

    MATCH (l:Label {v:'B. J. Habibie'}) <-[:rdfs_label]- (e),
        (e) -[:rdf_type]-> (c),
        (c)-[:rdfs_subClassOf*]->(t)
    RETURN e, c, t;

### Future Consideration: Reified Facts

Since a `Fact` is a relationship and not a node, it cannot be connected to any
other node (perhaps another reified fact). If that is required, a `Fact` can be turned into a
[singleton property](http://mor.nlm.nih.gov/pubs/pdf/2014-www-vn.pdf) fact or via RDF-style reification.

Meta-Property Relationships connects a (singleton property) Fact node with its subject, time, location, or keywords.

| No. | Yago2s Property          | Neo4j Relationship Type   |
|-----|--------------------------|---------------------------|
|   1 | `rdf:type`               | `type`                    |
|   2 | `extractionSource`       | `extractionSource`        |
|   3 | `occursIn`               | `occursIn`                |
|   4 | `placedIn`               | `placedIn`                |
|   5 | `occursSince`            | `occursSince`             |
|   6 | `occursUntil`            | `occursUntil`             |
|   7 | `startsExistingOnDate`   | `startsExistingOnDate`    |
|   8 | `endsExistingOnDate`     | `endsExistingOnDate`      |

### LiteralFact

A `LiteralFact` has `type`, `value`, and `language` as Neo4j properties.

### Recognized Properties

Here are the recognized properties along with Yago2s Semantic Structure Mapping to Neo4j Relationship Types.

| No. | Yago2s Property          | Neo4j Relationship Type |
|-----|--------------------------|-------------------------|
|   1 | `rdfs:label`             | `label`                 |
|   2 | `skos:prefLabel`         | `prefLabel`             |
|   4 | `graduatedFrom`          | `graduatedFrom`         |
|   5 | `hasGender`              | `hasGender`             |
|   6 | `isMarriedTo`            | `isMarriedTo`           |
|   7 | `wasBornOnDate`          | `wasBornOnDate`         |

### Messaging Topics & Queues

#### /topic/lumen.AGENT_ID.persistence.fact

TODO.

#### /queue/lumen.AGENT_ID.persistence.fact

##### Ask with Single Answer

Ask and require a single `Fact` answer: (`replyTo` required)

```json
{
  "@type": "Question",
  "multipleAnswers": false,
  "subject": {
    "@id": "http://yago-knowledge.org/resource/B.J._Habibie"
  },
  "property": {
    "@id": "http://yago-knowledge.org/resource/wasBornOnDate"
  }
}
```

Reply:

```json
{
  "@type": "Fact",
  "id": "id_1xidad2_1xk_uv85ns", 
  "subject": {
    "@type": "SemanticEntity",
    "@id": "http://yago-knowledge.org/resource/B.J._Habibie",
    "rdfs:label": "Bacharuddin Jusuf Habibie",
    "skos:prefLabel": "Bacharuddin Jusuf Habibie"
  },
  "property": {
    "@type": "SemanticProperty",
    "@id": "http://yago-knowledge.org/resource/wasBornOnDate",
  },
  "object": {
    "@type": "LocalDate",
    "value": "1936-06-25"
  }
}
```

##### Assert A Fact

```json
{
  "@type": "Fact",
  "subject": {
    "@id": "http://yago-knowledge.org/resource/B.J._Habibie"
  },
  "property": {
    "@id": "http://yago-knowledge.org/resource/wasBornOnDate",
  },
  "object": {
    "@type": "LocalDate",
    "value": "1936-06-25"
  }
}
```

If `replyTo` is given, will reply will a `Fact` summary:

```json
{
  "@type": "Fact",
  "id": "id_1xidad2_1xk_uv85ns", 
  "subject": {
    "@type": "SemanticEntity",
    "@id": "http://yago-knowledge.org/resource/#B.J._Habibie",
    "rdfs:label": "Bacharuddin Jusuf Habibie",
    "skos:prefLabel": "Bacharuddin Jusuf Habibie"
  },
  "property": {
    "@type": "SemanticProperty",
    "@id": "http://yago-knowledge.org/resource/wasBornOnDate",
  },
  "object": {
    "@type": "LocalDate",
    "value": "1936-06-25"
  }
}
```

## Knowledge Persistence

TODO.

Stored in Neo4j. Mimics [OpenCog AtomSpace Nodes and Links](http://wiki.opencog.org/w/AtomSpace).

It may be useful to reuse [Suggested Upper Merged Ontology (SUMO)](http://www.adampease.org/OP/)'s
_axiomatic knowledge_, which can be integrated with YAGO, see [YAGO-SUMO](http://people.mpi-inf.mpg.de/~gdemelo/yagosumo/).

## Neo4j Browser

It's nice to be able to use Neo4j Browser for the data, but you can't run both
Lumen Persistence and Neo4j Server at the same time.

    cp -va /var/lib/neo4j ~/neo4j-lumen
    rm -rv ~/neo4j-lumen/data
    mkdir -v ~/neo4j-lumen/data
    rm -v ~/neo4j-lumen/conf
    mkdir -v ~/neo4j-lumen/conf
    sudo cp -v /etc/neo4j/* ~/neo4j-lumen/conf/
    sudo chown -Rc budhiym:budhiym ~/neo4j-lumen

Edit `~/neo4j-lumen/conf/neo4j-server.properties`:

    org.neo4j.server.database.location=/home/budhiym/lumen_lumen_dev/neo4j/graph.db

Then run:

    sudo service neo4j-service stop
    ~/neo4j-lumen/bin/neo4j console

## conf/neo4j-wrapper.conf

You **will** need to configure `wrapper.java.maxmemory` to 1024, otherwise you'll get `OutOfMemoryError` with at least step 4 DB.

## Tuning Performance

During import and also for production server, you need to tweak user limit.

Create `/etc/security/limits.d/neo4j.conf` :

    neo4j   soft    nofile  40000
    neo4j   hard    nofile  40000
    budhiym soft    nofile  40000
    budhiym hard    nofile  40000

Edit `/etc/pam.d/su` and uncomment:

    session    required   pam_limits.so

Then restart your computer.

Now `ulimit -n` should show `40000`.

During import, maximize the RAM before writing disk:

    # Ubuntu's default: vm.dirty_background_ratio = 5 vm.dirty_ratio = 10
    sysctl vm.dirty_background_ratio vm.dirty_ratio
    # Set new values
    sudo sysctl vm.dirty_background_ratio=50 vm.dirty_ratio=80

Your heap (`-Xmx`) should be large, i.e. 75% of RAM.

For more info, see (Neo4j Linux Performance Guide](http://neo4j.com/docs/stable/linux-performance-guide.html).

## PostgreSQL + Yago3-based databases (Lumen Persistence v1.0.0)

Now we have a different structure, knowledge is split between:
 
1. a PostgreSQL database with multiple schemas containing multiple tables.
    Schemas are:
    
    a. `public`
    b. `lumen`
    c. `sanad`

    Note: Spring Data JPA is very slow for adding 100,000+ of relationships.
    Without proper transaction management, `ImportYagoTaxonomy2App::linkSubclasses` still not finished after 1 hour!
    Even after using native query but without transaction management, `ImportYagoTaxonomy2App::linkSubclasses` still hasn't
    reached 10000 relationships after 6 minutes!
    With proper transaction management, `ImportYagoTaxonomy2App::linkSubclasses` can do 5000 links/second.
    (total is ~570K links)
    With proper transaction management but Spring Data-style save, `ImportYagoTaxonomy2App::addLabels` (complete)
    needs 40 seconds to do 10000 labels.
    With proper transaction management and native query, `ImportYagoTaxonomy2App::addLabels` (complete)
    needs 6 seconds per 10000 labels.
    In total, PostgreSQL can do entirely (full labels) in ~6 minutes.
    As comparison, Neo4j can do the entire `ImportYagoTaxonomyApp` (unpartitioned, simplified labels) in 3 minutes!

2. a Neo4j database with multiple partitions (using TinkerPop PartitionStrategy)
   and using OpenCog-friendly schema as much as possible.

Ready database files is available from Hendy Irawan:
`hendywl\project_passport\lumen\persistence` (see `README.md` there)

1. Thing labels (all partitions: yago+common+var) in PostgreSQL.
   Table: `public.yagolabel`
   (I tried YAGO's original PostgreSQL importer before and too slow to import even before adding indexes, e.g. `yagoSources.tsv`.
   It doesn't even have a primary key!)
   So we need to be selective and optimize (e.g. use `ENUM`).

2. Things and taxonomy in Neo4j, label: `owl_Thing`, partitions: [`lumen_yago`, `lumen_common`, `lumen_var`].
    Example:
    
        MATCH (y {_partition: 'lumen_yago'}) DETACH DELETE y
        MATCH (v {_partition: 'lumen_var'}) DETACH DELETE v
    
        MERGE (hendy:owl_Thing {nn: 'lumen:Hendy_Irawan', _partition: 'lumen_var'}) // SET hendy.prefLabel='Hendy Irawan'
        MERGE (person:owl_Thing {nn: 'yago:wordnet_person_100007846', _partition: 'lumen_yago'}) // SET person.prefLabel='person'
        MERGE (hendy) -[:rdf_type]-> (person)

### How to Import

1. Edit `persistence/config/application.properties`, ensure `workspaceDir` is on the fast data drive
   (and if you dual-boot, shared), e.g.
   
        workspaceDir=D:/lumen_lumen_${tenantEnv}

2. (Optional) Set your Neo4j CE server VM configuration to `-Xmx4g` (in 16 GB system, 4 GB max heap is default)
3. Import `SemanticProperty` nodes and links from `yagoSchema.tsv`.
   This will `MERGE` `rdf_Property` nodes representing properties,
   `owl_Thing` nodes (some are `rdfs:Resource` because we have stuff like `xsd:string` too) representing types.
   Then `MERGE` links `rdf_Property` for `rdf:type` to `owl_Thing`.
   Then `MERGE` links `rdf_Property` for `rdfs:domain` to `owl_Thing`.
   Then `MERGE` links `rdf_Property` for `rdfs:range` to `owl_Thing`.
   Then `MERGE` links `rdf_Property` for `rdfs:subPropertyOf` to super `rdf_Property`.

4. Things taxonomy to Neo4j.
    These will `MERGE` `owl:Thing` nodes for types from `yagoTaxonomy.tsv`. (~ 1 min on i7 + RAM 16 GB + SSD)
    (`MERGE` is required here because importing `yagoSchema.tsv` created types too) 
    These will `CREATE` `owl:Thing` nodes for all things from `yagoTypes.tsv`. (~ 6 mins on i7 + RAM 16 GB + SSD)
    It will `CREATE` links `rdfs:subClassOf` between types. (~ 2 mins on i7 + RAM 16 GB + SSD)
    It will `CREATE` links `rdf:type` between types and things (and other types). (~ 26 mins on i7 + RAM 16 GB + SSD)
    Download these parts from https://www.mpi-inf.mpg.de/departments/databases-and-information-systems/research/yago-naga/yago/downloads/ :

        yagoTaxonomy.csv
        yagoTypes.csv
        
    For faster import, get the intermediate files from Hendy's: https://drive.google.com/open?id=0B9dx38a6NVxKWVprQWZwYi1heEE
    and extract to `lumen/persistence` folder (will fill the `work` subfolder).

5. YAGO3 labels to PostgreSQL.
    These will add `rdfs:label`, `skos:prefLabel`, `yago:isPreferredMeaningOf`, `yago:hasGloss`, `yago:hasGivenName`, `yago:hasFamilyName`,
    including metaphone, all indexed.
    Download these parts from https://www.mpi-inf.mpg.de/departments/databases-and-information-systems/research/yago-naga/yago/downloads/ :

        yagoLabels.tsv
        yagoGeonamesGlosses.tsv
        yagoDBpediaClasses.tsv
        yagoMultilingualClassLabels.tsv

    TODO: Importer

## OBSOLETE: To be determined whether we use PostgreSQL or adapt the Neo4j to use partitioned style

1. `~/lumen_lumen_{tenantEnv}/lumen/taxonomy.neo4j` (325 MB)
       Contains the entire `yagoTaxonomy.tsv`, plus the
       `rdfs:label`, `skos:prefLabel`, `isPreferredMeaningOf` from `yagoLabels.tsv` of those mentioned types.

    `ImportYagoTaxonomyApp YAGO3_FOLDER` using `-Xmx4g`
    Required input files: `yagoTaxonomy.tsv, yagoLabels.tsv`
    Sample YAGO3_FOLDER: `D:\databank\yago3_work`
    ~3 minutes on i7-6700HQ+16GB+SSD

## OBSOLETE: Steps to Import from Yago2s (Lumen Persistence v0.0.1)

This normally should not be required, and only used when database needs to be refreshed or there's a new Yago
version.

**Important:** Before importing, make sure to tweak Linux kernel `vm.*` options above!
Mount your Neo4j DB in `tmpfs` (4000M) to get ~1500 inserts/s (using 6 workers in 8 CPU), otherwise in HDD you get ~200/s,
but my testing in HDD can get to ~20/s on step 6.

tmpfs/SSD works well with multithreading (individual transaction per thread), while HDD probably want serialized better.

1. Index Labels -> 550 MiB `yago2s/yagoLabels.jsonset` (Hadoop-style Ctrl+A-separated JSON). ~2 mins on SSD
    TODO: this needs to index *all* labels across all files, not just `yagoLabels.tsv`, so next importers
    don't need to check for label properties
2. Import Labels -> 1.5 GiB Initial Neo4j database (including href constraint, Resource indexes, and Label.v indexes) using BatchInserter
    ~5 mins on SSD
    Run once: `neo4j-shell ~/lumen_lumen_dev/neo4j/graph.db` to "fix incorrect shutdown"
3. Next steps are to import other files, recommended to be in order.
    Import `yagoLabels.tsv` (3 special label properties will be ignored, it will only import regular labels like `hasFamilyName` etc.)
    ~1 hour on `tmpfs`, probably ~8 hrs on HDD (SSD crashed on me). Result: 3000 MiB DB.
4. Import `yagoLiteralFacts.tsv` # test first, but move after types & taxonomy when done
    Source is 321 MiB, 3.353.659 statements. ~45 mins SSD. Result is 5.034 MiB DB.
5. Import `yagoFacts.tsv`. Source: 321 MiB, 4.484.914 statements. ~45 mins SSD, result 7.042 MiB.
6. Import `yagoImportantTypes.tsv`. 169 MiB. 2,723,628 statements. ~1 hr SSD, result 8.096 MiB.
7. Import `yagoSimpleTypes.tsv`. 316 MiB, 5,437,179 statements. ~1 hr SSD, result 10.081 MiB.
8. Import `yagoTypes.tsv`. 821 MiB, 9,019,769 statements.
9. Import `yagoSimpleTaxonomy.tsv` (499 KiB, 6,576 statements, ~1 sec), `yagoTaxonomy.tsv` (49 Mib, 451,708 statements)
10. Import `yagoGeonamesClassIds.tsv` (36 KiB), `yagoGeonamesClasses.tsv` (52 KiB), `yagoGeonamesGlosses.tsv` (65 KiB), `yagoGeonamesEntityIds.tsv` (7 MiB)
    ~2 mins SSD.
11. Import `yagoWordnetIds.tsv` (4 MiB, 68,862 statements), `yagoWordnetDomains.tsv` (9 MiB, 87,573 statements).
12. Index the labels (these can be dropped first if later imports are needed):

        CREATE INDEX ON :Resource(prefLabel);
        CREATE INDEX ON :Resource(isPreferredMeaningOf);
        CREATE INDEX ON :Label(v);

Excluded Yago files are: (note: even if excluded, these can always be queried online through official Yago website)

1. `yagoWikipediaInfo.tsv` (2.3 GiB): just `linksTo` stuff, not useful
2. `yagoSources.tsv` (6.9 Gib): relates factIds to Wikipedia URIs
3. `yagoTransitiveType.tsv` (2.5 GiB): just the same as `yago(|Important|Simple)Types.csv` inferred using `yagoTaxonomy.tsv`
4. `yagoStatistics.tsv`: just meta about Yago dataset.
5. `yagoSchema.tsv`: just meta about Yago properties.
6. `yagoMultilingualInstanceLabels.tsv`: 443 MiB, 8,164,317 statements. English ones are OK (at least for now).
7. `yagoMultilingualClassLabels.tsv`: 46 MiB, 787,650 statements. English ones are OK (at least for now, but we do need to say that "car" is "mobil" soon).
8. `yagoGeonamesData.tsv`: 1.7 GiB, 32,216,600 statements. If you want exact lat-long for `yagoGeoEntity`s,
    you can already get the `geonamesEntityId` and look them up on Geonames DB.

