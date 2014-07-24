#!/bin/sh

rm -rf repo
mkdir repo
cd repo
cp ../*.sh .

echo "This is a test repository containing all kinds of (weird) branches, tags, etc. used to test the git client plugin.\n" > readme.txt
echo "Execute create.sh in an empty folder to recreate this repository.\n\n" >> readme.txt
echo "Copy the specialBranchRepo.zip and specialBranchRepo.ls-remote to src/test/resources/.\n\n" >> readme.txt
git init
git add .
git commit -m "Initial commit"
#gitk --all &

# Create branches

git checkout master
git checkout -b origin/master
touch a
git add .
git commit -m "Local branch 'origin/master'"

git checkout master
git checkout -b origin/xxx
touch a
git add .
git commit -m "Local branch 'origin/xxx'"

git checkout master
git checkout -b remotes/origin/master
touch a
git add .
git commit -m "Local branch 'remotes/origin/master'"

git checkout master
git checkout -b remotes/origin/xxx
touch a
git add .
git commit -m "Local branch 'remotes/origin/xxx'"

git checkout master
git checkout -b refs/heads/master
touch a
git add .
git commit -m "Local branch 'refs/heads/master'"

git checkout master
git checkout -b refs/heads/xxx
touch a
git add .
git commit -m "Local branch 'refs/heads/xxx'"

git checkout master
git checkout -b refs/remotes/origin/master
touch a
git add .
git commit -m "Local branch 'refs/remotes/origin/master'"

git checkout master
git checkout -b refs/remotes/origin/xxx
touch a
git add .
git commit -m "Local branch 'refs/remotes/origin/xxx'"

git checkout master
git checkout -b refs/heads/refs/heads/master
touch a
git add .
git commit -m "Local branch 'refs/heads/refs/heads/master'"

git checkout master
git checkout -b refs/heads/refs/heads/xxx
touch a
git add .
git commit -m "Local branch 'refs/heads/refs/heads/xxx'"

git checkout master
git checkout -b refs/tags/master
touch a
git add .
git commit -m "Local branch 'refs/tags/master'"

git checkout master
git checkout -b refs/tags/xxx
touch a
git add .
git commit -m "Local branch 'refs/tags/xxx'"

# Create Tags
git checkout master
git checkout -b tags

touch a
git add .
git commit -m "Tag test 'origin/master'"
git tag -a "origin/master" -m "Tag test 'origin/master'"

touch b
git add .
git commit -m "Tag test 'remotes/origin/master'"
git tag -a "remotes/origin/master" -m "Tag test 'remotes/origin/master'"

touch c
git add .
git commit -m "Tag test 'refs/heads/master'"
git tag -a "refs/heads/master" -m "Tag test 'refs/heads/master'"

touch d
git add .
git commit -m "Tag test 'refs/remotes/origin/master'"
git tag -a "refs/remotes/origin/master" -m "Tag test 'refs/remotes/origin/master'"

touch e
git add .
git commit -m "Tag test 'refs/heads/refs/heads/master'"
git tag -a "refs/heads/refs/heads/master" -m "Tag test 'refs/heads/refs/heads/master'"

touch f
git add .
git commit -m "Tag test 'refs/tags/master'"
git tag -a "refs/tags/master" -m "Tag test 'refs/tags/master'"

touch g
git add .
git commit -m "Tag test 'master'"
git tag -a "master" -m "Tag test 'master'"

# End
git checkout master

# Create files for src/test/resources/ and cleanup
jar cvf ../specialBranchRepo.zip ./
git ls-remote . > ../specialBranchRepo.ls-remote
cd ..
rm -rf ./repo