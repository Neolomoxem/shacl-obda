docker run --rm \
           -v /home/leemhuis/ontop/input:/opt/ontop/input \
           -v /home/leemhuis/ontop/jdbc:/opt/ontop/jdbc \
           -e ONTOP_ONTOLOGY_FILE=/opt/ontop/input/prototyp.owl \
           -e ONTOP_MAPPING_FILE=/opt/ontop/input/prototyp.obda \
           -e ONTOP_PROPERTIES_FILE=/opt/ontop/input/prototyp.properties \
           -p 8080:8080 \
           ontop/ontop