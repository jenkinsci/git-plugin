#!/bin/sh

rm -rf repo
mkdir repo
cd repo
cp ../*.sh .

echo "This is a test repository containing namespace branches used to test the git client plugin.\n" > readme.txt
echo "Execute create.sh in an empty folder to recreate this repository.\n\n" >> readme.txt
echo "Copy the namespaceBranchRepo.zip and namespaceBranchRepo.ls-remote to src/test/resources/.\n\n" >> readme.txt
git init
git add .
git commit -m "Initial commit"
#gitk --all &

# Create branches
git checkout master
git checkout -b a_tests/b_namespace1/master
touch a
git add .
git commit -m "Local branch 'a_tests/b_namespace1/master'"

git checkout master
git checkout -b a_tests/b_namespace2/master
touch a
git add .
git commit -m "Local branch 'a_tests/b_namespace2/master'"

git checkout master
git checkout -b a_tests/b_namespace3/master
touch a
git add .
git commit -m "Local branch 'a_tests/b_namespace3/master'"

git checkout master
git checkout -b b_namespace3/master
touch a
git add .
git commit -m "Local branch 'b_namespace3/master'"

git checkout master
git checkout -b branchForTagA
touch a
git add .
git commit -m "Local branch 'branchForTagA'"
git tag "TagA"

git checkout master
git checkout -b branchForTagBAnnotated
touch a
git add .
git commit -m "Local branch 'branchForTagBAnnotated'"
git tag -a TagBAnnotated -m 'TagBAnnotated'


# End
git checkout master

# Create files for src/test/resources/ and cleanup
jar cvf ../namespaceBranchRepo.zip ./
git ls-remote . > ../namespaceBranchRepo.ls-remote
cd ..
rm -rf ./repo