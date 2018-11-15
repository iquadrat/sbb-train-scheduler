# Train Schedule Solver

Solver for problem instances described on
https://github.com/crowdAI/train-schedule-optimisation-challenge-starter-kit .

## Building

From the root of the clone repository run:
```shell
mvn package
```

## Running

The program takes two JSON files, the first is the input containing the problem instance,
the second is the output of the solution.

Additionally, it takes following optional flags:
* `max_penalty`: Upper bound on the penalty. The solver searches only for solutions which have a penalty less (or equal) to this value.
* `max_penalty_per_intention`: Maximum penalty for a single [service intention](https://github.com/crowdAI/train-schedule-optimisation-challenge-starter-kit/blob/master/documentation/input_data_model.md#service_intentions).
* `connection_badness_factor`: Scaling factor between connections and resource conflicts.
* `director_type`: Back-tracking strategy. One of `PRIORITY_CONFLICT`, `HARD_COLLECTING`.

Examples:
```shell
java -jar target/solver-0.0.1-SNAPSHOT.jar \
     problem_instances/03_FWA_0.125.json solution_03.json
```

This will compute a zero-penalty solution and write it to `solution_03.json`.

```shell
java -jar target/solver-0.0.1-SNAPSHOT.jar \
     problem_instances/05_V1.02_FWA_with_obstruction.json solution_05.json \
     --max_penalty_per_intention 13.0 --max_penalty 37.5 --connection_badness_factor 3.5 \
     --director_type PRIORITY_CONFLICT
```


