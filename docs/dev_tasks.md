Bubble Developer Tasks
======================
How to update the codebase, how to run the API server, how to reset the database.

## Updating the Code
If you want to grab the latest code, and ensure that all git submodules are properly in sync with the main repository, run:
```shell script
./bin/git_update_bubble.sh
```

This will update and rebuild all submodules, and the main bubble jar file.

## Running the API server
Run the `./bin/run.sh` script to start the Bubble server.

## Reset everything
If you want to "start over", run:
```shell script
./bin/reset_bubble_full
```

This will remove local files stored by Bubble, and drop the bubble database.

If you run `./bin/run.sh` again, it will be like running it for the first time.
