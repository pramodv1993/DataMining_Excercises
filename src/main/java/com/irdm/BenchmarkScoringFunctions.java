package com.irdm;

import com.opencsv.CSVReader;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The class assumes the csv documents "assesments", "documents" and "queries" have been placed in the resources folder of the project.
 * Further for benchmarking purposes the command line args takes the type of scoring model, and a REMOVE STOP WORD flag and MAX_LIMIT as params.
 */
public class BenchmarkScoringFunctions {
    private static Map<Long, Map<CONTENT_TYPE,String>> documents = new HashMap<Long, Map<CONTENT_TYPE, String>>();
    private static Map<Long, Map<CONTENT_TYPE, String>> queries = new HashMap<>();
    private static Map<Long,List<Long>> queriesVsRelevantDocs = new HashMap<>();
    //lookup
    private static Map<String, Set<Long>> indexedTermsVsDocIds = new HashMap<>();

    private static String DOCUMENTS = "/documents.csv";
    private static String QUERIES = "/queries.csv";
    private static String ASSESSMENTS = "/assessments.csv";
    private static String[] stopwords;
    private static boolean REMOVE_STOPWORDS = true;
    private static Double AVG_DOC_LENGTH = 0.0d;
    private static Long TOTAL_DOCS = 0L;
    private static Long MAX_MATCHED_DOC_LIMIT = 10L;
    static{
        stopwords = new String[]{"i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "they", "them", "their", "theirs", "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for", "with", "about", "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here", "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now"};
    }

    enum MODEL{
        TF_IDF, OKAPI_BM25(1.2f,.75f), TF_IDF_DAMP_IDF, TF_IDF_SUB_LINEAR_SCALE_TF;
        private float k1 = 0.0f;
        private float b = 0.0f;
        MODEL(float k1,float b) {
            this.k1 = k1;
            this.b = b;
        }
        MODEL(){}

        public float getK1() {
            return k1;
        }
        public float getB() {
            return b;
        }
    }
    enum CONTENT_TYPE{
        STEMMED, NON_STEMMED, STEMMED_KEYWORDS, NON_STEMMED_KEYWORDS
    }

    private static String removeStopWords(String inp){
        for(String stopWord : stopwords)
            inp = inp.replaceAll(" +" + stopWord + " +"," ");
        return inp;
    }

    /**
     * construct <docId , content> map
     * @throws IOException
     */

    private static void readDocs() throws IOException {
        final Long[] docLengths = {0l};
        CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(BenchmarkScoringFunctions.class.getResourceAsStream(DOCUMENTS))));
        csvReader.readAll()
                .forEach(record -> {
                    String[] lineSplits = record[0].split(";");
                    if(lineSplits[0] == null)
                        try {
                            throw  new Exception("Doc Id is empty");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    if(lineSplits.length == 1)
                        return;
                    Long docId = Long.parseLong(lineSplits[0]);
                    String stemmedContent = (lineSplits[1] == null)? "" : lineSplits[1];
                    String nonStemmedContent = (lineSplits[2] == null) ? "" : lineSplits[2];
                    Map<CONTENT_TYPE,String> doc_content = new HashMap<>();
                    stemmedContent = (REMOVE_STOPWORDS) ? removeStopWords(stemmedContent) : stemmedContent;
                    doc_content.put(CONTENT_TYPE.STEMMED,stemmedContent.toLowerCase());
                    doc_content.put(CONTENT_TYPE.NON_STEMMED,nonStemmedContent.toLowerCase());
                    docLengths[0] += stemmedContent.split(" ").length;
                    documents.put(docId,doc_content);

                });
        TOTAL_DOCS = (long) documents.size();
        AVG_DOC_LENGTH = (double) docLengths[0] / TOTAL_DOCS;
    }

    /**
     * construct <queryId , content> map
     * @throws IOException
     */
    private static void readQueries() throws IOException {
        CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(BenchmarkScoringFunctions.class.getResourceAsStream(QUERIES))));
        csvReader.readAll()
                .forEach(record->{
                    String[] lineSplits = record[0].split(";");
                    if(lineSplits.length == 0)
                        return;
                    Long queryId = Long.parseLong(lineSplits[0]);
                    String stemmedContent = (lineSplits[1] == null) ? "": lineSplits[1];
                    String nonStemmedContent = (lineSplits[2] == null) ? "" : lineSplits[2];
                    String stemmedKeywords = (lineSplits[3] == null) ?  "" : lineSplits[3];
                    String nonStemmedKeywords = (lineSplits[4] == null)? "" : lineSplits[4];
                    Map<CONTENT_TYPE,String> query_contents = new HashMap<>();
                    query_contents.put(CONTENT_TYPE.STEMMED,stemmedContent);
                    query_contents.put(CONTENT_TYPE.NON_STEMMED,nonStemmedContent);
                    query_contents.put(CONTENT_TYPE.STEMMED_KEYWORDS,stemmedKeywords);
                    query_contents.put(CONTENT_TYPE.NON_STEMMED_KEYWORDS,nonStemmedKeywords);
                    queries.put(queryId,query_contents);
                });
    }

    /**
     * Construct <queryId , <relevantDocIds>> map
     * @throws IOException
     */
    private static void readAssessments() throws IOException {
        CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(BenchmarkScoringFunctions.class.getResourceAsStream(ASSESSMENTS))));
        csvReader.readAll()
                .forEach(record->{
                    String[] lineSplits = record[0].split(";");
                    if(lineSplits.length == 0)
                        return;
                    Long queryId = Long.parseLong(lineSplits[0]);
                    if(queriesVsRelevantDocs.containsKey(queryId))
                        queriesVsRelevantDocs.get(queryId).add(Long.parseLong(lineSplits[1]));
                    else{
                        List<Long> relevantDocIds = new ArrayList<>();
                        relevantDocIds.add(Long.parseLong(lineSplits[1]));
                        queriesVsRelevantDocs.put(queryId,relevantDocIds);
                    }
                });

    }

    /**
     * construct <index term , <docIds>> map
     * the stemmed terms from documents are used for indexing and later lookup
     */
    private static void indexTermsFromDocuments(){
        documents.forEach((docId, content) -> {
            String processedContent = (REMOVE_STOPWORDS) ? removeStopWords(content.get(CONTENT_TYPE.STEMMED)) : content.get(CONTENT_TYPE.STEMMED);
            for(String term : processedContent.split(" ")){
                if(indexedTermsVsDocIds.containsKey(term.toLowerCase()))
                    indexedTermsVsDocIds.get(term.toLowerCase()).add(docId);
                else{
                    Set<Long> docIds = new HashSet<>();
                    docIds.add(docId);
                    indexedTermsVsDocIds.put(term.toLowerCase(),docIds);
                }
            }
        });


    }

    /**
     *
     *
     * for each term in the query
     *      get the documents containing the term
     *      for each document
     *          compute scores based on the chosen model for the given document and term
     *          append to the final score for the document
     * @return matched <docId , score> map for the query
     **/
    private static Map<Long,Double> matchDocsForQuery(Long queryId, MODEL model){
        Map<Long,Double> docIdVsScores = new HashMap<>();
        String query = queries.get(queryId).get(CONTENT_TYPE.STEMMED_KEYWORDS);
        String[] terms = (REMOVE_STOPWORDS)? removeStopWords(query).split(" "):query.split(" ");
        for(String term : terms){
            if(indexedTermsVsDocIds.containsKey(term.toLowerCase())){
                indexedTermsVsDocIds.get(term.toLowerCase())
                        .forEach(docId ->{
                            Double score = 0.0d;
                            try {
                                score = computeScoreForQuery(model,docId,term);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if(docIdVsScores.containsKey(docId))
                                //updating the score of a document with respect to the query
                                docIdVsScores.put(docId , docIdVsScores.get(docId) + score);
                            else
                                docIdVsScores.put(docId , score);
                        });
            }
//            else{
//                System.out.println("Unindexed term " + term);
//                handleUnIndexedTerm(documents,term);
//
//            }
        }
        return docIdVsScores;
    }

    /**
     *
     * @param docId
     * @param term
     * @return score based on model selected
     * @throws Exception
     */
    private static Double computeScoreForQuery(MODEL model,Long docId, String term) throws Exception {
        Double score = 0.0d;
        if(!indexedTermsVsDocIds.containsKey(term))
            throw new Exception("Index for term " + term + " not present");
        if(!documents.containsKey(docId))
            throw new Exception("Document with id " + docId + " not present");
        int docFreq = indexedTermsVsDocIds.get(term).size();
        String document = documents.get(docId).get(CONTENT_TYPE.STEMMED);
        if(     model.compareTo(MODEL.TF_IDF) == 0
                || model.compareTo(MODEL.TF_IDF_DAMP_IDF) == 0
                || model.compareTo(MODEL.TF_IDF_SUB_LINEAR_SCALE_TF) == 0
        )
            score = (double)termFrequency(model,term,document) * IDF(docFreq,model);

        else if(model.compareTo(MODEL.OKAPI_BM25) == 0){
            long docLength = document.split(" ").length;
             score =
                    ((model.getK1() + 1) * termFrequency(model,term,document) )
                    / (((model.getK1() * ((1-model.getB()) +(model.getB() * docLength / AVG_DOC_LENGTH))) + termFrequency(model,term,document)));
            score = score * Math.log((TOTAL_DOCS - docFreq + 0.5) / (docFreq + 0.5));
        }
        return score;
    }
    private static Double IDF(int termFreq, MODEL model){
        if(model.compareTo(MODEL.TF_IDF) == 0)
            return (double)TOTAL_DOCS / termFreq;
        return Math.log(TOTAL_DOCS / termFreq);
    }
    private static Double termFrequency(MODEL model,String term, String text){
        String[] splits = text.split(" ");
        long count = 0L;
        for(String split : splits)
            if(split.toLowerCase().equals(term.toLowerCase()))
                count += 1;

        return ((count > 0L) && (model.compareTo(MODEL.TF_IDF_SUB_LINEAR_SCALE_TF) == 0)) ? (1 + Math.log(count)) : count;
    }
    private static void benchmarkWithScoringFunction(MODEL model){
        Double totalPrecision = 0.0d;
        Double totalRecall = 0.0d;
        for(Long queryId : queries.keySet()){
            System.out.println("processing query :" + queryId);
            Map<Long,Double> matchingDocs = new HashMap<>();
            matchingDocs = matchDocsForQuery(queryId, model);
            LinkedHashMap<Long, Double> sortedDocs = sortDocsByScores(matchingDocs);
            List<Long> relevantDocs = queriesVsRelevantDocs.get(queryId);
            Pair metrics = calc_precision_recall(relevantDocs,sortedDocs.keySet().stream().limit(MAX_MATCHED_DOC_LIMIT).collect(Collectors.toList()));
            totalPrecision += (Double) metrics.getKey();
            totalRecall += (Double) metrics.getValue();
        }
        System.out.println("Average Precision " + totalPrecision/queries.size());
        System.out.println("Average Recall " + totalRecall/queries.size());
    }

    private static Pair<Double,Double> calc_precision_recall(List<Long> relevant, List<Long> retrieved){
        int tp,fp,fn,tp_plus_fp;
        double precision, recall;
        List<Long> retrieved_copy = new ArrayList<>(retrieved);

        tp_plus_fp = retrieved.size();

        //fp = retrieved - relevant
        retrieved.removeAll(relevant);
        fp = retrieved.size();
        //retrieved - fp
        tp = retrieved_copy.size() - fp;
        //fn = relevant - relevant retrieved
        relevant.removeAll(retrieved_copy);
        fn = relevant.size();

        precision = (double)tp/tp_plus_fp;
        recall = (double) tp / (tp + fn);

        return new MutablePair<>(precision, recall);
    }
    private static  <T> LinkedHashMap<T,Double>  sortDocsByScores(Map<T,Double> inp){
        return inp.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue,(v1,v2)->v1,LinkedHashMap::new));
    }
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //expected properties : Model, REMOVE_STOPWORDS(true/false), MAX_MATCHED_DOC_LIMIT(default - 50)
        readDocs();
        readQueries();
        readAssessments();
        indexTermsFromDocuments();
        if(System.getProperties().containsKey("REMOVE_STOPWORDS")){
            if(!System.getProperty("REMOVE_STOPWORDS").isEmpty())
                REMOVE_STOPWORDS = Boolean.valueOf(System.getProperty("REMOVE_STOPWORDS"));
        }
        if(System.getProperties().containsKey("MAX_MATCHED_DOC_LIMIT")){
            if(!System.getProperty("MAX_MATCHED_DOC_LIMIT").isEmpty())
                MAX_MATCHED_DOC_LIMIT = Long.valueOf(System.getProperty("MAX_MATCHED_DOC_LIMIT"));
        }
        if(System.getProperties().containsKey("Model")){
            if(!System.getProperty("Model").isEmpty())
                benchmarkWithScoringFunction(MODEL.valueOf(System.getProperty("Model")));
        }
        else
            System.out.println("Please select one of the following models : TF_IDF, OKAPI_BM25, TF_IDF_DAMP_IDF, TF_IDF_SUB_LINEAR_SCALE_TF");

    }
}
