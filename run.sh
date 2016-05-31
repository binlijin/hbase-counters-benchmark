#! /usr/bin/env bash
# 
# Run the benchmark

mvn clean install -DskipTests

for i in 1 8 max;   do
  java -jar target/benchmarks.jar -i 10 -wi 10 -t $i 2>&1 | tee results-threads-$i.log;
done
