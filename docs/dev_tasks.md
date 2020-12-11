Bubble Developer Tasks
======================
How to update the codebase, how to run the API server, how to reset the database.

## Updating the Code
You can use normal git tools just fine with Bubble. Be aware that the bubble git
repository makes extensive use of git submodules. So we provide some tools to
make simple things simple. Everything is still git, there is no magic here.

If you want to grab the latest code and ensure that all git submodules are properly
in sync with the main repository:
 * First, ensure that you have no locally modified files (or `git stash` your changes)
 * Then run:
```shell script
./bin/git_update_bubble.sh
```
This will update and rebuild all submodules, and the main bubble jar file.

## Building
If you've changed files in `bubble-server/src/`, and you want to see those changes live,
you'll need to rebuild:
```shell script
bbuild
```

## Rebuilding Utilities
If you change source files in one of the submodules under `utils`, you'll need to
rebuild (and `mvn install`) those submodules, **and then** run `bbuild` to incorporate
the changed libraries into the Bubble jar.

## Rebuilding the Web Site
If you change files in `bubble-web`, you don't need to run a full `bbuild`.
Instead you can run the much faster web-build.

Run this from the `bubble-web` directory:
```shell script
rm -f ./dist/* && npm run build
```
This will remove all previous site files and have npm regenerate the HTML/CSS/JS for the
Bubble web UI.

If you look in `${HOME}/.bubble.env`, you'll see that the `BUBBLE_ASSETS_DIR` variable points
to `${HOME}/bubble/bubble-web/dist`. Thus, when you run `npm run build` to update the files in `dist`,
the "live" site is updated.

## Running the API server
To start the Bubble server:
```shell script
./bin/run.sh
```
This will run the server in the foreground. Hit Control-C to stop it.

## Reset everything
If you want to "start over", run:
```shell script
./bin/reset_bubble_full
```

This will remove local files stored by Bubble, and drop the bubble database.

If you run `./bin/run.sh` again, it will be like running it for the first time.

## Other tools
There are many other tools in the `bin` directory.

Most tools accept a `-h` / `--help` option and will print usage information.
