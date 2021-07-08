#!/usr/bin/env bash
cd ../conf

echo 'Zipping configset'
rm config.zip
zip config.zip *

#echo 'Deleting existing collection'
#curl -X GET "http://localhost:8987/solr/admin/collections?action=DELETE&name=biocache3"

#echo 'Deleting existing configset'
curl -X GET "http://localhost:8988/solr/admin/configs?action=DELETE&name=biocache&omitHeader=true"

echo 'Creating  configset'
curl -X POST --header "Content-Type:application/octet-stream" --data-binary @config.zip "http://localhost:8988/solr/admin/configs?action=UPLOAD&name=biocache"

#echo 'Creating  collection'
#curl -X GET "http://localhost:8986/solr/admin/collections?action=CREATE&name=biocache&numShards=8&maxShardsPerNode=1&replicationFactor=1&collection.configName=biocache"

cd ../..
rm solr/conf/config.zip
cd scripts

echo 'Done'



