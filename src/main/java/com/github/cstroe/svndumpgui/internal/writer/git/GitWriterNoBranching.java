package com.github.cstroe.svndumpgui.internal.writer.git;

import com.github.cstroe.svndumpgui.api.ContentChunk;
import com.github.cstroe.svndumpgui.api.Node;
import com.github.cstroe.svndumpgui.api.NodeHeader;
import com.github.cstroe.svndumpgui.api.Revision;
import com.github.cstroe.svndumpgui.internal.utility.Tuple2;
import com.github.cstroe.svndumpgui.internal.writer.AbstractRepositoryWriter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.javatuples.Triplet;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Git writer that treats all SVN paths as files.  No branches, no tags.
 */
public class GitWriterNoBranching extends AbstractRepositoryWriter {
    private final File gitDir;
    private final String mainBranch;
    private final Git git;
    private final int startFromRev;
    private final AuthorIdentities identities;
    private final NodeSeparator nodeSeparator;
    private final NodeBatcher nodeBatcher;

    private Revision currentRevision;
    private Node currentNode;
    private Map<String, List<Tuple2<Integer, String>>> branchToRevisionToSha = new HashMap<>(9182);

    public GitWriterNoBranching(String gitDir, AuthorIdentities identities) throws IOException, GitAPIException {
        this(gitDir, "main", identities);
    }

    public GitWriterNoBranching(String gitDir, String mainBranch, AuthorIdentities identities) throws IOException, GitAPIException {
        this(gitDir, mainBranch, 0, identities);
    }

    public GitWriterNoBranching(String gitDir, String mainBranch, int startFromRev, AuthorIdentities identities) throws GitAPIException, IOException {
        this.gitDir = new File(gitDir);
        this.startFromRev = startFromRev;
        this.mainBranch = mainBranch;
        this.identities = identities;
        this.nodeSeparator = new NodeSeparatorImpl(mainBranch);
        this.nodeBatcher = new NodeBatcherImpl();

        if (!(this.gitDir.exists() && this.gitDir.isDirectory())) {
            throw new RuntimeException("Directory does not exist: " + this.gitDir.getAbsolutePath());
        }

        this.git = Git.init()
                .setInitialBranch(this.mainBranch)
                .setDirectory(this.gitDir)
                .call();
    }

    @Override
    public void consume(Revision revision) {
        super.consume(revision);
        if (revision.getNumber() < startFromRev) {
            currentRevision = null;
            return;
        }
        currentRevision = revision;
    }

    @Override
    public void consume(Node node) {
        super.consume(node);
        if (currentRevision != null) {
            currentNode = node;
        }
    }

    @Override
    public void endNode(Node node) {
        super.endNode(node);
        if (currentRevision != null) {
            currentRevision.addNode(node);
            node.setRevision(currentRevision);
        }
    }

    @Override
    public void consume(ContentChunk chunk) {
        super.consume(chunk);
        if (currentNode != null) {
            currentNode.addFileContentChunk(chunk);
        }
    }

    @Override
    public void finish() {
        super.finish();
        git.close();
    }

    /**
     * Handle translation of this SVN revision into Git.
     */
    @Override
    public void endRevision(Revision revision) {
        super.endRevision(revision);

        if (currentRevision == null) {
            ps().println(String.format("[%5s] Revision is skipped.", revision.getNumber()));
            return;
        }

        if (revision.getNumber() == 0) {
            createInitialCommit(revision);
            return;
        }

//        List<Node> revisionNodes = revision.getNodes().stream().filter(node -> {
//            boolean isDirCreate = node.isDir() && node.getHeaders().get(NodeHeader.COPY_FROM_REV) == null;
//            if (isDirCreate) {
//                ps().println(String.format("[%5s] Skipping empty directory node: %s", node.getRevision().get().getNumber(), node.getPath().get()));
//            }
//            return !isDirCreate;
//        }).collect(Collectors.toList());
//        if (revisionNodes.isEmpty()) {
//            ps().println(String.format("[%5d] Skipping empty revision.", revision.getNumber()));
//            return;
//        }

        List<Quartet<ChangeType, String, String, Node>> separatedNodes = nodeSeparator.separate(revision.getNodes());
        List<Pair<ChangeType, List<Triplet<String, String, Node>>>> batches = nodeBatcher.batch(separatedNodes);

        for (Pair<ChangeType, List<Triplet<String, String, Node>>> batch : batches) {
            switch (batch.getValue0()) {
                case TRUNK:
                    processTrunkBatch(revision, batch.getValue1());
                    continue;
                default:
                    throw new RuntimeException(batch.getValue0() + " is not implemented");
            }
        }

//        List<Triplet<String, String, Node>> currentBatch = new ArrayList<>();
//        String currentBatchBranch = null;
//        String currentBatchTag = null;
//        for (Quartet<ChangeType, String, String, Node> node : nodeSeparator.separate(revisionNodes)) {
//            if (node.getValue0() == ChangeType.BRANCH_CREATE) {
//                processBatch(revision, currentBatchBranch, currentBatchTag, currentBatch);
//                currentBatchBranch = null;
//                currentBatchTag = null;
//                currentBatch.clear();
//                throw new RuntimeException("branch creation is not implemented");
//            } else if (node.getValue0() == ChangeType.BRANCH) {
//                if (currentBatchBranch == null) {
//                    // accumulate changes
//                    currentBatchBranch = node.getValue1();
//                    currentBatch.add(Triplet.with(node.getValue1(), node.getValue2(), node.getValue3()));
//                    continue;
//                } else if (!currentBatchBranch.equals(node.getValue1())) {
//                    processBranchNodes(revision, currentBatch);
//                    currentBatch.clear();
//                    currentBatchBranch = node.getValue1();
//                    currentBatch.add(Triplet.with(node.getValue1(), node.getValue2(), node.getValue3()));
//                    continue;
//                }
//            } else if (node.getValue0() == ChangeType.TAG_CREATE) {
//                if (currentBatchBranch != null) {
//                    processBranchNodes(revision, currentBatch);
//                    currentBatch.clear();
//                    currentBatchBranch = null;
//                }
//                throw new RuntimeException("tag creation is not implemented");
//            } else if (node.getValue0() == ChangeType.TAG) {
//                if (currentBatchBranch != null) {
//                    processBranchNodes(revision, currentBatch);
//                    currentBatch.clear();
//                    currentBatchBranch = null;
//                }
//            }
//        }
    }

    private void processTrunkBatch(Revision revision, List<Triplet<String, String, Node>> batch) {
        // checkout trunk branch (if not already checked out)
        try {
            if (!this.mainBranch.equals(git.getRepository().getBranch())) {
                git.checkout().setName(this.mainBranch).call();
            }
            ps().println(String.format("[%5d] Checked out branch: %s", revision.getNumber(), this.mainBranch));
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e);
        }
        // create temp branch
        String workingBranch = this.mainBranch + "-rev-" + revision.getNumber();
        try {
            Ref gitBranchCreated = git.checkout().setName(workingBranch).setCreateBranch(true).call();
            ps().println(String.format("[%5d] Created new branch %s.", revision.getNumber(), workingBranch));
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }

        // process each node in the batch
        boolean changed = false;
        for (Triplet<String, String, Node> node : batch) {
            if (node.getValue2().isDir()) {
                createDirectoryFromHistory(node.getValue2(), node.getValue1());
            } else if (node.getValue2().isFile()) {
                addNewFile(node.getValue2(), node.getValue1());
                changed = true;
            } else {
                throw new RuntimeException("not implemented");
            }
        }
        // merge temp branch to trunk
        doCommit(revision, this.mainBranch, workingBranch, changed);
    }

//    private void processBatch(Revision revision, String currentBatchBranch, String currentBatchTag, List<Triplet<String, String, Node>> currentBatch) {
//        if (currentBatchBranch != null) {
//            processBranchNodes(revision, currentBatch);
//        } else if (currentBatchTag != null) {
//            processTagNodes(revision, currentBatch);
//        }
//    }

    private void processTagNodes(Revision revision, List<Triplet<String, String, Node>> currentBatch) {
        throw new RuntimeException("processing tag nodes is not implemented");
    }

//    private void processBranchNodes(Revision revision, List<Triplet<String, String, Node>> nodes) {
//        String parentBranch = entry.getValue0();
//        try {
//            if (!parentBranch.equals(git.getRepository().getBranch())) {
//                git.checkout().setName(parentBranch).call();
//            }
//            ps().println(String.format("[%5d] Checked out branch: %s", revision.getNumber(), parentBranch));
//        } catch (GitAPIException | IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        // start a new branch
//        String workingBranch = parentBranch + "-rev-" + revision.getNumber();
//        try {
//            Ref gitBranchCreated = git.checkout().setName(workingBranch).setCreateBranch(true).call();
//        } catch (GitAPIException e) {
//            throw new RuntimeException(e);
//        }
//        ps().println(String.format("[%5d] Created new branch %s.", revision.getNumber(), workingBranch));
//
//        boolean changed = false;
//        for (Pair<Node, String> nodeWithPath : entry.getValue()) {
//            boolean hasChanged = processNode(nodeWithPath.first, parentBranch, workingBranch, nodeWithPath.second);
//            changed = changed || hasChanged;
//        }
//
//        doCommit(revision, parentBranch, workingBranch, changed);
//    }

    private void createInitialCommit(Revision revision) {
        try {
            Process touch = new ProcessBuilder()
                    .command("/usr/bin/touch", ".gitignore")
                    .directory(gitDir)
                    .start();
            int retVal = touch.waitFor();
            if (retVal != 0) {
                throw new RuntimeException("could not run: touch .gitignore, return value = " + retVal);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            final PersonIdent ident = identities.from(revision);
            gitAddAll();
            RevCommit revCommit = quickCommit(ident, "Initial commit.\nSVN revision: " + revision.getNumber());
            branchToRevisionToSha.computeIfAbsent(this.mainBranch, s -> new ArrayList<>())
                            .add(Tuple2.of(revision.getNumber(), revCommit.getName()));
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }

        ps().println(String.format("[%5s] Created an initial commit.", revision.getNumber()));
    }

    /**
     * @return true if a change was committed
     */
//    private boolean processNode(Node node, String parentBranch, String workingBranch, String path) {
//        if (node.isDir()) {
//            if (!node.getCopyFromRev().isPresent()) {
//                throw new RuntimeException("node should have been filtered out:\n" + node);
//            }
//
//            if (branchPattern.matcher(node.getPath().get()).matches()) {
//                try {
//                    String currentBranch = git.getRepository().getBranch();
//                    createBranch(node);
//                    // checkout previous branch
//                    if (!currentBranch.equals(git.getRepository().getBranch())) {
//                        git.checkout().setName(currentBranch).call();
//                    }
//                } catch (GitAPIException | IOException e) {
//                    throw new RuntimeException(e);
//                }
//                return false;
//            }
//
//            Matcher tagMatcher = tagPattern.matcher(node.getPath().get());
//            if (tagMatcher.matches()) {
//                String tagName = tagMatcher.group(1);
//                String copyFromPath = node.getCopyFromPath().get();
//                if (tagPattern.matcher(copyFromPath).matches() || isInTagPattern.matcher(copyFromPath).matches()) {
//                    throw new UnsupportedOperationException("don't know how to handle tag creation from other tags");
//                }
//                Pair<String, String> branchInfo = removeBranchPrefix(copyFromPath);
//                String branchName = branchInfo.first;
//                String branchPath = branchInfo.second;
//                int copyFromRev = Integer.parseInt(node.getCopyFromRev().get());
//
//                String gitSha = findGitSha(branchName, copyFromRev)._2;
//
//                // git tag tagName gitSha
//                PersonIdent tagger = identities.from(node.getRevision().get());
//                try {
//                    try(RevWalk walk = new RevWalk(git.getRepository())) {
//                        ObjectId id = git.getRepository().resolve(gitSha);
//                        RevCommit commit = walk.parseCommit(id);
//                        git.tag().setName(tagName).setTagger(tagger).setObjectId(commit).call();
//                    } catch (GitAPIException e) {
//                        throw new RuntimeException(e);
//                    }
//                    ps().println(String.format("[%5d] Created tag '%s' for commit %s",
//                            node.getRevision().get().getNumber(), tagName, gitSha));
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//                return false;
//            }
//
//            createDirectoryFromHistory(node, path);
//            return true;
//        }
//
//        // write the file
//        Optional<String> maybeAction = node.getAction();
//        if (!maybeAction.isPresent()) {
//            return false;
//        }
//        switch (maybeAction.get()) {
//            case "add": {
//                Matcher tagPatternMatcher = isInTagPattern.matcher(path);
//                if (tagPatternMatcher.matches()) {
//                    final String tagName = tagPatternMatcher.group(1);
//                    final String tagPath = tagPatternMatcher.group(2);
//                    throw new UnsupportedOperationException(String.format("can't add a file [%s] in a tag: %s", tagPath, tagName));
//                } else {
//                    addNewFile(node, path);
//                    return true;
//                }
//            }
//            case "change": {
//                Matcher tagPatternMatcher = isInTagPattern.matcher(path);
//                if (tagPatternMatcher.matches()) {
//                    final String tagName = tagPatternMatcher.group(0);
//                    final String tagPath = tagPatternMatcher.group(1);
//                    throw new UnsupportedOperationException(String.format("can't change a file [%s] in a tag: %s", tagPath, tagName));
//                } else {
//                    changeExistingFile(node, path);
//                    return true;
//                }
//            }
//            case "delete": {
//                Matcher tagPatternMatcher = tagPattern.matcher(path);
//                if (tagPatternMatcher.matches()) {
//                    final String tagName = tagPatternMatcher.group(1);
//                    deleteTag(node, path, tagName);
//                    return false;
//                }
//                Matcher inTagPatternMatcher = isInTagPattern.matcher(path);
//                if (inTagPatternMatcher.matches()) {
//                    String tagName = inTagPatternMatcher.group(1);
//                    String tagPath = inTagPatternMatcher.group(2);
//                    throw new UnsupportedOperationException(String.format("cannot change file [%s] in tag: %s", tagPath, tagName));
//                }
//
//                deleteNode(node, path);
//                return true;
//            }
//            case "replace":
//                replaceNode(node, path);
//                return true;
//            default:
//                throw new RuntimeException("Can't handle node action: " + maybeAction.get());
//        }
//    }

//    private void deleteTag(Node node, String path, String tagName) {
//        try {
//            git.tagDelete().setTags(tagName).call();
//            ps().println(String.format("[%5d] Deleted tag: %s", node.getRevision().get().getNumber(), tagName));
//        } catch (GitAPIException e) {
//            throw new RuntimeException(e);
//        }
//    }

//    private void createBranch(Node node) {
//        Matcher branchMatcher = branchPattern.matcher(node.getPath().get());
//        if (!branchMatcher.find()) {
//            throw new RuntimeException("createBranch called with non-branch node");
//        }
//
//        String branchName = branchMatcher.group(1);
//
//        try {
//            List<Ref> branches = git.branchList().call();
//            List<Ref> matchingBranches = branches.stream()
//                    .filter(branch -> branch.getName().equals(branchName))
//                    .collect(Collectors.toList());
//            if (matchingBranches.size() > 0) {
//                throw new RuntimeException(
//                        "Could not create branch '" + branchName + "'. Branch already exists: " +
//                                branches.stream().map(Ref::getName).collect(Collectors.joining(", ")));
//            }
//
//            int revision = Integer.parseInt(node.getHeaders().get(NodeHeader.COPY_FROM_REV));
//            Tuple2<Integer, String> sha = findGitSha(this.mainBranch, revision);
//            if (sha == null) {
//                throw new RuntimeException("Could not find sha for revision " + revision);
//            }
//
//            Ref ref = git.branchCreate()
//                    .setStartPoint(sha._2)
//                    .setName(branchName)
//                    .call();
//
//            ps().println(String.format("[%5s] Created branch '%s' from %s", node.getRevision().get().getNumber(), ref.getName(), sha));
//        } catch (GitAPIException e) {
//            throw new RuntimeException(e);
//        }
//    }

    private Tuple2<Integer, String> findGitSha(String branch, int revision) {
        List<Tuple2<Integer, String>> shas = branchToRevisionToSha.get(branch);
        if (shas == null) {
            throw new RuntimeException("branch does not exist: " + branch);
        }

        if (shas.size() < 1) {
            throw new RuntimeException("could not find at least one sha for branch: " + branch);
        }

        Tuple2<Integer, String> currentSha = shas.get(0);
        for (int i = 1; i < shas.size(); i++) {
            Tuple2<Integer, String> nextSha = shas.get(i);
            if (nextSha._1 > revision) {
                break;
            }
            currentSha = nextSha;
        }
        return currentSha;
    }

    private void replaceNode(Node node, String nodePath) {
        changeExistingFile(node, nodePath);
    }

    private void createDirectoryFromHistory(Node node, String nodePath) {
        final int revNum = node.getRevision().get().getNumber();
        String copyFromRev = node.getHeaders().get(NodeHeader.COPY_FROM_REV);
        String copyFromPathRaw = node.getHeaders().get(NodeHeader.COPY_FROM_PATH);
        if (copyFromRev == null || copyFromPathRaw == null) {
            throw new RuntimeException("A revision is missing a path");
        }

        Pair<String, String> copyFromPath = null; // = removeBranchPrefix(copyFromPathRaw);
        final String sourceBranch = null; //copyFromPath.first;
        final String sourcePath = null; //copyFromPath.second;

        Integer copyFromRevision = Integer.valueOf(node.get(NodeHeader.COPY_FROM_REV));

        Tuple2<Integer, String> copyFromGitSha = findGitSha(sourceBranch, copyFromRevision);

        try {
            ps().println(String.format("[%5d] Restoring directory '%s' from: %s:%s at %s",
                    revNum, nodePath, copyFromPath, copyFromRev, copyFromGitSha));

            String[] gitCommand = {"/usr/bin/git", "ls-tree", "-r", copyFromGitSha._2, sourcePath};
            ps().println(String.format("[%5d] Executing '%s'", revNum, String.join(" ", gitCommand)));
            Process gitLsTree = new ProcessBuilder(gitCommand)
                    .directory(this.gitDir)
                    .start();

            List<List<String>> result = new BufferedReader(new InputStreamReader(gitLsTree.getInputStream()))
                    .lines()
                    .map(l -> l.split("\t"))
                    .map(l ->
                            Stream.concat(
                                    Arrays.stream(l[0].split(" ")),
                                    Arrays.stream(new String[] { l[1] })
                            ).collect(Collectors.toList())
                    )
                    .collect(Collectors.toList());

            ps().println(String.format("[%5d] Found %d files.", revNum, result.size()));
            Iterator<List<String>> resultIter = result.listIterator();

            final Revision revision = node.getRevision().get();
            final PersonIdent ident = identities.from(revision);
            for (int i = 0; i < result.size(); i++) {
                ps().println(String.format("[%5d] Processing file %d of %d.", revNum, i + 1, result.size()));
                List<String> fileInfo = resultIter.next();
                String mode = fileInfo.get(0);
                String blobSha = fileInfo.get(2);
                String originalFile = fileInfo.get(3);
                String newFile = nodePath + File.separator + originalFile.substring(sourcePath.length() + 1);
                File absoluteNewFile = new File(gitDir.getAbsolutePath() + File.separator + newFile);

                ps().println(String.format("[%5d] copying bytes from %s@%s to %s", revNum, originalFile, sourceBranch, newFile));


                if (!absoluteNewFile.getParentFile().exists() && !absoluteNewFile.getParentFile().mkdirs()) {
                    throw new RuntimeException("could not create directory: " + absoluteNewFile.getParentFile().getAbsolutePath());
                }

                if (!absoluteNewFile.exists() && !absoluteNewFile.createNewFile()) {
                    throw new RuntimeException("could not create file: " + absoluteNewFile.getAbsolutePath());
                }

                String[] showCommand = {"/usr/bin/git", "show", blobSha};
                ps().println(String.format("[%5d] Executing '%s'", node.getRevision().get().getNumber(), String.join(" ", showCommand)));
                Process showProc = new ProcessBuilder(showCommand)
                        .redirectOutput(absoluteNewFile)
                        .directory(this.gitDir)
                        .start();
                int showProcRetVal = showProc.waitFor();
                if (showProcRetVal != 0) {
                    throw new RuntimeException("could not execute: '" + String.join(" ", showCommand) + "', return value: " + showProcRetVal);
                }

                handleMode(mode, absoluteNewFile);
                Status st = git.status().call();
                if (!st.isClean()) {
                    gitAddAll();
                    quickCommit(ident, String.format("copied data from [%s@%s] to [%s]", originalFile, sourceBranch, newFile));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ps().println(String.format("[%5d] Finished processing files.", node.getRevision().get().getNumber()));
    }

    private void handleMode(String mode, File file) {
        switch(mode) {
            case "100644":
                // do nothing
                break;
            case "100755":
                if (!file.setExecutable(true)) {
                    throw new RuntimeException("could not set executable bit for: " + file.getAbsolutePath());
                }
                break;
            default:
                throw new RuntimeException("don't know how to handle mode: " + mode);
        }
    }

    private void addNewFile(Node node, String nodePath) {
        if (node.getHeaders().get(NodeHeader.COPY_FROM_REV) != null) {
            //addNewFileFromHistory(node, nodePath);
            throw new RuntimeException("not implemented");
            //return;
        }
        String absolutePath = gitDir.getAbsolutePath() + File.separator + nodePath;

        File newFile = new File(absolutePath);
        if (!newFile.getParentFile().exists() && !newFile.getParentFile().mkdirs()) {
            throw new RuntimeException("Could not create directory: " + newFile.getParentFile());
        };

        try(FileOutputStream fos = new FileOutputStream(newFile)){
            fos.write(node.getByteContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        setExecutable(node, newFile);

        final Revision revision = node.getRevision().get();
        final PersonIdent ident = identities.from(revision);
        try {
            gitAddAll();
            quickCommit(ident, "add new file [" + nodePath + "] (SVN revision " + revision.getNumber() + ")");
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private void setExecutable(Node node, File file) {
        if (node.getProperties().get("svn:executable") != null) {
            if (node.getProperties().get("svn:executable").equals("*")) {
                if (!file.setExecutable(true)) {
                    throw new RuntimeException("cannot set file as executable: " + file.getAbsolutePath());
                }
            } else {
                throw new RuntimeException("don't know how to handle 'svn:executable' property set to '" + node.getProperties().get("svn:executable") + "'");
            }
        }
    }

    private RevCommit quickCommit(PersonIdent ident, String message) throws GitAPIException {
        RevCommit commit = git.commit()
                .setAuthor(ident)
                .setCommitter(ident)
                .setMessage(message)
                .call();
        ps().println(message);
        return commit;
    }

//    private void addNewFileFromHistory(Node node, String nodePath) {
//        final int revNum = node.getRevision().get().getNumber();
//        String copyFromRev = node.getHeaders().get(NodeHeader.COPY_FROM_REV);
//        String copyFromPathRaw = node.getHeaders().get(NodeHeader.COPY_FROM_PATH);
//
//        if (copyFromRev == null || copyFromPathRaw == null) {
//            throw new RuntimeException("A revision is missing a path");
//        }
//
//        Pair<String, String> copyFromPath = removeBranchPrefix(copyFromPathRaw);
//        final String sourceBranch = copyFromPath.first;
//        final String sourcePath = copyFromPath.second;
//
//        int copyFromRevision = Integer.parseInt(node.get(NodeHeader.COPY_FROM_REV));
//        Tuple2<Integer, String> copyFromGitSha = findGitSha(copyFromPath.first, copyFromRevision);
//
//        try {
//            ps().println(String.format("[%5d] Restoring file %s@%s from %s",
//                    node.getRevision().get().getNumber(), copyFromPath, copyFromRev, copyFromGitSha));
//
//            String[] gitCommand = {"/usr/bin/git", "ls-tree", "-r", copyFromGitSha._2, copyFromPath.second};
//            ps().println(String.format("[%5d] Executing '%s'", node.getRevision().get().getNumber(), String.join(" ", gitCommand)));
//
//            Process gitLsTree = new ProcessBuilder(gitCommand)
//                    .directory(this.gitDir)
//                    .start();
//
//            List<List<String>> result = new BufferedReader(new InputStreamReader(gitLsTree.getInputStream()))
//                    .lines()
//                    .map(l -> l.split("\t"))
//                    .map(l ->
//                            Stream.concat(
//                                    Arrays.stream(l[0].split(" ")),
//                                    Arrays.stream(new String[] { l[1] })
//                            ).collect(Collectors.toList())
//                    )
//                    .collect(Collectors.toList());
//
//            if (result.size() > 1) {
//                throw new RuntimeException("found more than one file when running: " + String.join(" ", gitCommand));
//            }
//
//            final Revision revision = node.getRevision().get();
//            final PersonIdent ident = identities.from(revision);
//
//            for (List<String> fileInfo : result) {
//                String mode = fileInfo.get(0);
//                String blobSha = fileInfo.get(2);
//                String originalFile = fileInfo.get(3);
//
//                File absoluteNewFile = new File(gitDir.getAbsolutePath() + File.separator + nodePath);
//                ps().println(String.format("[%5d] copying bytes from %s@%s to %s", revNum, originalFile, sourceBranch, nodePath));
//
//                if (!absoluteNewFile.getParentFile().exists() && !absoluteNewFile.getParentFile().mkdirs()) {
//                    throw new RuntimeException("could not create directory: " + absoluteNewFile.getParentFile().getAbsolutePath());
//                }
//
//                if (!absoluteNewFile.createNewFile()) {
//                    throw new RuntimeException("could not create file: " + absoluteNewFile.getAbsolutePath());
//                }
//
//                String[] showCommand = {"/usr/bin/git", "show", blobSha};
//                ps().println(String.format("[%5d] Executing '%s'", node.getRevision().get().getNumber(), String.join(" ", showCommand)));
//                Process showProc = new ProcessBuilder(showCommand)
//                        .redirectOutput(absoluteNewFile)
//                        .directory(this.gitDir)
//                        .start();
//                int showProcRetVal = showProc.waitFor();
//                if (showProcRetVal != 0) {
//                    throw new RuntimeException("could not execute: '" + String.join(" ", showCommand) + "', return value: " + showProcRetVal);
//                }
//
//                handleMode(mode, absoluteNewFile);
//                Status st = git.status().call();
//                if (!st.isClean()) {
//                    quickCommit(ident, String.format("copied data from [%s@%s] to [%s]", originalFile, sourceBranch, nodePath));
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

    private void changeExistingFile(Node node, String nodePath) {
        String absolutePath = gitDir.getAbsolutePath() + File.separator + nodePath;
        File file = new File(absolutePath);
        writeFile(file, node.getByteContent());

        setExecutable(node, file);

        final Revision revision = node.getRevision().get();
        final PersonIdent ident = identities.from(revision);
        try {
            gitAddAll();
            quickCommit(ident, "change file [" + nodePath + "] (SVN revision " + revision.getNumber() + ")");
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeFile(File newFile, byte[] content) {
        try(FileOutputStream fos = new FileOutputStream(newFile, false)){
            fos.write(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    private void deleteNode(Node node, String nodePath) {
//        try {
//            Status status = git.status().call();
//            if (status.getAdded().contains(nodePath)) {
//                git.reset().addPath(nodePath).call();
//            }
//        } catch (GitAPIException e) {
//            throw new RuntimeException(e);
//        }
//
//        String absolutePath = gitDir.getAbsolutePath() + File.separator + nodePath;
//
//        File toDelete = new File(absolutePath);
//        if (!toDelete.exists()) {
//            ps().println(String.format("[%5d] Cannot delete non-existent file: %s",
//                    node.getRevision().get().getNumber(), nodePath));
//            return;
//        }
//
//        if (toDelete.isDirectory()) {
//            boolean deleted = deleteDirectory(toDelete);
//            if (!deleted) {
//                throw new RuntimeException("Did not delete directory: " + absolutePath);
//            }
//        } else {
//            boolean deleted = new File(absolutePath).delete();
//            if (!deleted) {
//                throw new RuntimeException("Did not delete file: " + absolutePath);
//            }
//        }
//
//        final Revision revision = node.getRevision().get();
//        final PersonIdent ident = identities.from(revision);
//        try {
//            gitAddAll();
//            quickCommit(ident, "deleted [" + nodePath + "] (SVN revision " + revision.getNumber() + ")");
//        } catch (GitAPIException e) {
//            throw new RuntimeException(e);
//        }
//    }

    private void gitAddAll() {
        try {
            Process gitAdd = new ProcessBuilder().command("/usr/bin/git", "add", ".").directory(gitDir).start();

            int retVal = gitAdd.waitFor();
            if (retVal != 0) {
                throw new RuntimeException("could not execute: 'git add .' , return value: " + retVal);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // from https://www.baeldung.com/java-delete-directory
//    boolean deleteDirectory(File directoryToBeDeleted) {
//        File[] allContents = directoryToBeDeleted.listFiles();
//        if (allContents != null) {
//            for (File file : allContents) {
//                deleteDirectory(file);
//            }
//        }
//        return directoryToBeDeleted.delete();
//    }

    private void doCommit(Revision revision, String parentBranch, String workingBranch, boolean changed) {
        if (!changed) {
            try {
                git.checkout().setName(parentBranch).call();
                git.branchDelete().setBranchNames(workingBranch).call();
                ps().println(String.format("[%5d] Deleted empty branch '%s', back to '%s'.", revision.getNumber(), workingBranch, parentBranch));
                ps().println(String.format("[%5d] Did not change anything in this revision.", revision.getNumber()));
            } catch (GitAPIException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        try {
            git.checkout().setName(parentBranch).call();
            ps().println(String.format("[%5d] Checked out '%s' branch.", revision.getNumber(), parentBranch));

            String[] gitCommand = {"/usr/bin/git", "merge", "--squash", "-q", workingBranch};
            ps().print(String.format("[%5d] Executing '%s' ...", revision.getNumber(), String.join(" ", gitCommand)));
            ps().flush();
            Process gitMergeCommand = new ProcessBuilder(gitCommand).directory(this.gitDir).start();
            int retVal = gitMergeCommand.waitFor();
            if (retVal != 0) {
                throw new RuntimeException(String.format(
                        "could not execute: '%s', return value: %d",
                        String.join(" ", gitCommand), retVal));
            }
            ps().println("done.");

            PersonIdent author = identities.from(revision);
            ps().print(String.format("[%5d] Committing the merge commit ... ", revision.getNumber()));
            ps().flush();
            RevCommit revCommit = quickCommit(author, getMessage(revision));
            ps().println("done.");

            git.branchDelete().setForce(true).setBranchNames(workingBranch).call();
            ps().println(String.format("[%5d] Committed: %s", revision.getNumber(), revCommit.getName()));

            branchToRevisionToSha.computeIfAbsent(parentBranch, s -> new ArrayList<>())
                    .add(Tuple2.of(revision.getNumber(), revCommit.getName()));

        } catch (GitAPIException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String getMessage(Revision revision) {
        return revision.get("svn:log") + "\nSVN revision: " + revision.getNumber();
    }
}