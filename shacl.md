# SHACL Material für Mena

## Allgemein

Link zur [SHACL-Spezifikation](https://www.w3.org/TR/shacl/)

Spezifische Abschnitte hab ich bei den relevanten Teilen nochmal verlinkt.

### Annotiertes Constraint

Das folgende Constraint legt fest, dass für jedes Objekt der Klasse `:MSM-Material` (Focus-Nodes) mindestens drei Objekte der Klasse `:Gitterkonstante` (Value-Nodes) über `hat_Parameter` (Pfad) erreichbar sein sollen.

```python
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.
@prefix sh:  <http://www.w3.org/ns/shacl#>.
@prefix xsd: <http://www.w3.org/2001/XMLSchema#>. 
@prefix : <urn:absolute/prototyp#> . 

:testShape # Die URI dieser Shape
    
    # Optional, macht aus dieser Shape explizit eine NodeShape (wird aber meistens angegeben überall)
	a sh:NodeShape ;
	
    # Zieldefinition dieses Constraints: Etwas soll gelten für alle Objekte der Klasse :MSM-Material
    # Hier gibt es 5 Möglichkeiten (Link 1), davon unterstützen wir alle außer ImplicitClassTarget (Ist nur Syntax-Sugar) 
    sh:targetClass :MSM-Material;
    
    # Constraintsemantik in Propertyshape
	sh:property [            
        
        # Optional, wird eigentlich immer weggelassen
        a sh:PropertyShape

        # Pfad von einer Focus-Node zu einer Value-Node
        # Hier gibt es auch mehrere Möglichkeiten [Link 3] von denen wir alle außer den SPARQL1.1-Pfaden unterstützen.
		sh:path :hat_Parameter;

        # Aber hier kommen die sogennanten Constraint-Components, also die Semantik über den Value-Nodes [link 2]

        # Value-Node soll Klasse :Gitterkonstante sein
		sh:class :Gitterkonstante;

        # Davon solll es mindestens drei geben.
		sh:minCount 3;
	] .

```

Links:
- [1 Targetdefinitionen](https://www.w3.org/TR/shacl/#targets)
- [2 Components ](https://www.w3.org/TR/shacl/#core-components)
- [3 Pfade](https://www.w3.org/TR/shacl/#property-paths)

## Workshop

### Erstes Constraint

Ein ganz einfaches Constraint:

```py

# Jeder Parameter brauch einen Namen

:A
	a sh:NodeShape ;
	sh:targetClass 	:Parameter ;
	sh:property [                 
		sh:path :hat_Name;           
		sh:minCount 1;
	] .

# Alles was rechts von :hat_Parameter steht, sollte auch ein :Parameter sein

# (Soll es das tatsächlich? Es gibt ein paar Physikalische Größen da)

:A
	a sh:NodeShape ;
	sh:targetSubjectsOf	:hat_Parameter ;
	sh:property [                 
		sh:path :hat_Parameter;           
		sh:class :Parameter;
	] .


```



### Logische Junktoren

Wir können mehrer PropertyShapes kombinieren.

Es gehen [AND, OR, NOT, XONE](https://www.w3.org/TR/shacl/#core-components-logical)

```py

# Für jedes verwendete MSM-Material müssen die drei Gitterkonstanten hinterlegt sein.



# Das hier ist ja schön und gut, aber wir wollen ja was über die Konstanen a,b und c aussagen.

:testShape
	a sh:NodeShape ;
	sh:targetClass :MSM-Material;
	sh:property [                 # _:b1
		sh:path :hat_Parameter;
		sh:class :Gitterkonstante;
		sh:minCount 3;
		sh:maxCount 3;
	] .

# Implizites AND indem wir mehrere PropertyShapes angeben.

:testShape
	a sh:NodeShape ;
	sh:targetClass :MSM-Material;
	sh:property [                 # _:b1
		sh:path :hat_Parameter;
		sh:class :Gitterkonstante_a;
		sh:minCount 1;
		sh:maxCount 1;
	];
	sh:property [                 # _:b1
		sh:path :hat_Parameter;
		sh:class :Gitterkonstante_b;
		sh:minCount 1;
		sh:maxCount 1;
	];
	sh:property [                 # _:b1
		sh:path :hat_Parameter;
		sh:class :Gitterkonstante_c;
		sh:minCount 1;
		sh:maxCount 1;
	] .

# Ordentliches UND.

:testShape
	a sh:NodeShape ;
	sh:targetClass :MSM-Material;

	sh:and (
		[                 # _:b1
			sh:path :hat_Parameter;
			sh:class :Gitterkonstante_a;
			sh:minCount 1;
			sh:maxCount 1;
		]
		[                 # _:b1
			sh:path :hat_Parameter;
			sh:class :Gitterkonstante_b;
			sh:minCount 1;
			sh:maxCount 1;
		]
		[                 # _:b1
			sh:path :hat_Parameter;
			sh:class :Gitterkonstante_c;
			sh:minCount 1;
			sh:maxCount 1;
		]
	) .

```

Noch ein Beispiel mit AND und OR.
(Das geht mit sh:node intuitiver, aber das funktioniert leider gerade nicht.)

```py
# Die Blockierkraft sollte z.B. immer mit dem Aktuationsquerschnitt und entweder mit der anliegenden elektrischen Spannung oder dem elektrischen Feld hinterlegt sein, ansonsten ist der Wert nicht aussagekräftig.

:testShape
	a sh:NodeShape ;
	sh:targetClass 	:Element ;

	sh:not [
		sh:and (
			[
				sh:path :hat_Parameter;
				sh:class :Blockierkraft
			]
			sh:not [
				sh:or (
					[
						sh:path	:hat_Parameter;
						sh:class :elektrisches_Feld
					]
					[
						sh:path :hat_Parameter;
						sh:class :elektrische_Spannung
					]
				)
			]
		)
	].
	
```


### Pfade
Viele property-paths führen nach Rom

```python

# Eine Blockierkraft darf nicht einem Elastomermaterial zugeordnet sein.

# ist_Probe_von_Material
:BlockElasto
    a sh:NodeShape ;
    sh:targetClass :Elastomermaterial ;
    sh:not [
        sh:path :hat_Parameter ;
        sh:class :Blockierkraft;
    ] .

# Inverser Path von Material
:BlockElasto
    a sh:NodeShape ;
    sh:targetClass :Blockierkraft ;
    sh:not [
        sh:path [sh:inversePath :hat_Parameter] ;
        sh:class :Elastomermaterial;
    ] .

```
Alternativpfade und Sequenzpfade gehen zwar, sind aber bei den bisherigen Use-Cases noch nicht so wirklich einsetzbar.

### Literal-Constraints

Für Literale in den Value-Nodes können wir auch Constraints definieren.

**Leider grad kaputt, hab ich aber bis morgen gefixed!**

In den Use-Cases von PEK steht was von  $A^{me}_{11}>0$
Ich weiß nicht genau was das ist, deshalb hier exemplarisch mit der Blockierkraft:

```python

:testShape
	a sh:NodeShape ;
	sh:targetClass :Blockierkraft;
	sh:property [               
		sh:path :hat_Wert;
        sh:minExclusive 0;
	] .

```