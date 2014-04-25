#!/bin/bash
#
# Script to bulk out a simple know image data set.
#

COUNT=10000

while [ $COUNT -gt -1 ]
do


cp constant/big-0000.png constant/big-${COUNT}.png
cp epubs/test-0000.epub epubs/test-${COUNT}.epub

echo constant/big-${COUNT}.png done 

(( COUNT -- ))

done

