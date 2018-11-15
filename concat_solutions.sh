#!/bin/bash
touch submission.json
rm submission.json

echo "[" >> submission.json
foo=($@)
N=${#foo[@]}

i=0
for file in "$@"
do
    cat ${file} >> submission.json
		i=$((i+1))
		echo $i
		if [ $i -lt $((N)) ]
 	  then
			echo "," >> submission.json
		fi
done
echo "]" >> submission.json
