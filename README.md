**Benchmarking Scoring models for Information Retrieval**

- The application considers the following models - 

(i)TF_IDF
(ii)OKAPI_BM25
(iii)TF_IDF_DAMP_IDF
(iV)TF_IDF_SUB_LINEAR_SCALE_TF

- Execution is done through a runnable jar with the following command line arguments - 
 a) "Model"
	- that can take the args -  TF_IDF or  OKAPI_BM25 or TF_IDF_DAMP_IDF or TF_IDF_SUB_LINEAR_SCALE_TF
 b) an optional argument "REMOVE_STOPWORDS" and  "MAX_MATCHED_DOC_LIMIT"

- To see the output run one of the following commands 

	1. java -DModel=TF_IDF -DMAX_MATCHED_DOC_LIMIT=10 -DREMOVE_STOPWORDS=true -jar irdm_ex2-1.0-SNAPSHOT-jar-with-dependencies.jar
	2. java -DModel=OKAPI_BM25 -DMAX_MATCHED_DOC_LIMIT=10 -DREMOVE_STOPWORDS=true -jar irdm_ex2-1.0-SNAPSHOT-jar-with-dependencies.jar
	3. java -DModel=TF_IDF_DAMP_IDF -DMAX_MATCHED_DOC_LIMIT=10 -DREMOVE_STOPWORDS=true -jar irdm_ex2-1.0-SNAPSHOT-jar-with-dependencies.jar
	4. java -DModel=TF_IDF_SUB_LINEAR_SCALE_TF -DMAX_MATCHED_DOC_LIMIT=10 -DREMOVE_STOPWORDS=true -jar irdm_ex2-1.0-SNAPSHOT-jar-with-dependencies.jar

or any combinations of the "Model", "REMOVE_STOPWORDS" and "MAX_MATCHED_DOC_LIMIT" arguments to see their respective performances (Avg Precision and Recall)



In order to see the code of the application, please check the class file \src\main\java\com\irdm\BenchmarkScoringFunctions.java.
The application internally takes into consideration the document, queries and assessment files inside the resources folder of the project.
