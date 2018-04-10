# Solr Nightly Benchmarks  [![Travis branch](https://img.shields.io/travis/rust-lang/rust/master.svg)]()

A comprehensive Solr performance benchmark framework.

Please read the following sections in order and follow instructions. 

## [A] Server/OS Requirements

      * Java Version: 1.8.0_131 or above
      * GNU/Linux
      * lsof, lshw utilities
      * Apache Ivy 2.4.0 or above
      * Ant Version 1.9.2 or above
      * Apache Maven 3.3.9 or above
      * git version 2.11.1 or above
      * RAM 16 GB and above
      * At least a quad-core CPU
      * At least 10GB of free disk space
      
## [B] Steps to launch

     Note: Please checkout in a location with ample free disk space (at least 10GB)

     1. git clone https://github.com/chatman/lucene-solr.git --branch SolrNightlyBenchmarks-R2 solr-nightly-benchmarks
     2. cd solr-nightly-benchmarks/dev-tools/solrnightlybenchmarks
     3. cd data; ../scripts/download.sh; cd ..
     4. nohup mvn jetty:run & > logs/jetty.log
     5. mvn clean compile assembly:single
     6. java -cp target/org.apache.solr.tests.nightlybenchmarks-0.0.1-SNAPSHOT-jar-with-dependencies.jar \
             org.apache.solr.tests.nightlybenchmarks.BenchmarksMain <commitid> 
     7. mvn jetty:stop     # To stop the webserver that hosts the results/reports
     

## [C] Parameters

     TODO

## [D] Accessing Output

     * Please open http://localhost:4444 on your favorite browser.
  
## [D-1] Sample Output
     * The page that you will access will look like the following. 

![Alt text](http://www.viveknarang.com/gsoc/snb_screenshot5.PNG)
