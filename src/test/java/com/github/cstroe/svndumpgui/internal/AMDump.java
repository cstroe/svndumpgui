package com.github.cstroe.svndumpgui.internal;

import com.github.cstroe.svndumpgui.api.SvnDumpConsumer;
import com.github.cstroe.svndumpgui.api.SvnDumpValidator;
import com.github.cstroe.svndumpgui.api.SvnDumpWriter;
import com.github.cstroe.svndumpgui.api.SvnNodeHeader;
import com.github.cstroe.svndumpgui.generated.ParseException;
import com.github.cstroe.svndumpgui.generated.SvnDumpFileParser;
import com.github.cstroe.svndumpgui.internal.transform.ClearRevision;
import com.github.cstroe.svndumpgui.internal.transform.NodeAdd;
import com.github.cstroe.svndumpgui.internal.transform.NodeHeaderChange;
import com.github.cstroe.svndumpgui.internal.transform.NodeRemove;
import com.github.cstroe.svndumpgui.internal.transform.PathChange;
import com.github.cstroe.svndumpgui.internal.transform.UpdateAuthorForEmptyRevisions;
import com.github.cstroe.svndumpgui.internal.utility.SvnDumpFileCharStream;
import com.github.cstroe.svndumpgui.internal.validate.TerminatingValidator;
import com.github.cstroe.svndumpgui.internal.writer.SvnDumpAuthors;
import com.github.cstroe.svndumpgui.internal.writer.SvnDumpSummary;
import com.github.cstroe.svndumpgui.internal.validate.PathCollision;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class AMDump {

    /**
     * This is a cleanup that I did for the AgreementMaker repository.
     *
     * The original repository was exported to full.dump.
     * Then I filtered large files out with svndumpfilter to get onestep.dump:
     *
     *     cat initial_filter_list.txt > onestep.txt
     *     svndumpfilter exclude --targets initial_filter_list.txt < full.dump > onestep.dump
     *     grep -Poa "Node-path: \K.*\.rdf$" onestep.dump >> onestep.txt
     *     grep -Poa "Node-path: \K.*\.jar$" onestep.dump  >> onestep.txt
     *     grep -Poa "Node-path: \K.*\.zip$" onestep.dump >> onestep.txt
     *     svndumpfilter exclude --targets onestep.txt < full.dump > onestep.dump
     *
     * The code below operates on onestep.dump.
     */

    @Test
    @Ignore
    public void convert_AgreementMaker_repository() throws ParseException, NoSuchAlgorithmException, IOException {

        // add the main branch here
        SvnNodeImpl trunkAgreementMaker = new SvnNodeImpl();
        trunkAgreementMaker.getHeaders().put(SvnNodeHeader.ACTION, "add");
        trunkAgreementMaker.getHeaders().put(SvnNodeHeader.KIND, "dir");
        trunkAgreementMaker.getHeaders().put(SvnNodeHeader.PATH, "trunk/AgreementMaker");
        trunkAgreementMaker.getHeaders().put(SvnNodeHeader.PROP_CONTENT_LENGTH, "10");
        trunkAgreementMaker.getHeaders().put(SvnNodeHeader.CONTENT_LENGTH, "10");
        trunkAgreementMaker.setProperties(new HashMap<>());

        SvnDumpConsumer chain = new NodeAdd(4, trunkAgreementMaker);

        // IM commits
        chain.tail().continueTo(new ClearRevision(1007,1101));

        // AM-Core
        chain.tail().continueTo(new ClearRevision(1));
        chain.tail().continueTo(new ClearRevision(1119));

        // AgreementMaker-tags/AM1
        chain.tail().continueTo(new ClearRevision(1116, 1117));

        // AgreementMakerCVS
        chain.tail().continueTo(new ClearRevision(1850, 1851));

        // workingBranch
        chain.tail().continueTo(new ClearRevision(2037));
        chain.tail().continueTo(new ClearRevision(2041, 2044));
        chain.tail().continueTo(new ClearRevision(2048,2049));
        chain.tail().continueTo(new ClearRevision(2066));
        chain.tail().continueTo(new ClearRevision(2069,2070));

        // Ontologies
        chain.tail().continueTo(new ClearRevision(1847,1848));
        chain.tail().continueTo(new ClearRevision(2031));
        chain.tail().continueTo(new ClearRevision(2134));

        // BSM
        chain.tail().continueTo(new ClearRevision(2161,2162));
        chain.tail().continueTo(new ClearRevision(2169));
        chain.tail().continueTo(new ClearRevision(3069));

        // Matcher-Hierarchy
        chain.tail().continueTo(new ClearRevision(2769,2770));

        // Double AgreementMaker-OSGi
        chain.tail().continueTo(new ClearRevision(3057));
        chain.tail().continueTo(new ClearRevision(3244));

        // Remove double readme
        chain.tail().continueTo(new ClearRevision(434));

        chain.tail().continueTo(new ClearRevision(2346));
        chain.tail().continueTo(new ClearRevision(1199));
        chain.tail().continueTo(new ClearRevision(1567));

        // remove accidental deletion
        // (this was discovered after the release to GitHub, so those commits are still there)
        chain.tail().continueTo(new ClearRevision(2835));
        chain.tail().continueTo(new ClearRevision(2837));

        chain.tail().continueTo(new NodeRemove(440, "add", "trunk/AgreementMaker"));
        chain.tail().continueTo(new NodeRemove(440, "add", "trunk/AgreementMaker/images"));
        chain.tail().continueTo(new NodeRemove(440, "add", "trunk/AgreementMaker/images/aboutImage.gif"));
        chain.tail().continueTo(new NodeRemove(440, "add", "trunk/AgreementMaker/images/advis.png"));
        chain.tail().continueTo(new NodeRemove(440, "add", "trunk/AgreementMaker/images/agreementMaker.png"));
        chain.tail().continueTo(new NodeRemove(440, "add", "trunk/AgreementMaker/images/fileImage.gif"));

        // put everything under trunk
        chain.tail().continueTo(new PathChange("AgreementMaker", "trunk/AgreementMaker"));
        chain.tail().continueTo(new PathChange("NYTInstanceMatcher", "trunk/AgreementMaker/NYTInstanceMatcher"));
        chain.tail().continueTo(new PathChange("MyInstanceMatcher", "trunk/AgreementMaker/MyInstanceMatcher"));
        chain.tail().continueTo(new PathChange("AgreementMaker-SEALSBridge", "trunk/AgreementMaker/AgreementMaker-SEALSBridge"));
        chain.tail().continueTo(new PathChange("AgreementMaker-Matchers", "trunk/AgreementMaker/AgreementMaker-Matchers"));
        chain.tail().continueTo(new PathChange("AM_ROOT", "trunk/AgreementMaker/AM_ROOT"));
        chain.tail().continueTo(new PathChange("AgreementMaker-OSGi", "trunk/AgreementMaker-OSGi"));
        chain.tail().continueTo(new PathChange("AgreementMaker-CollaborationServer", "trunk/AgreementMaker-CollaborationServer"));

        // fix initial history :(
        chain.tail().continueTo(new PathChange("trunk/ScratchPad.txt", "trunk/AgreementMaker/ScratchPad.txt"));
        chain.tail().continueTo(new PathChange("trunk/archives", "trunk/AgreementMaker/archives"));
        chain.tail().continueTo(new PathChange("trunk/ciao", "trunk/AgreementMaker/ciao"));
        chain.tail().continueTo(new PathChange("trunk/images", "trunk/AgreementMaker/images"));
        chain.tail().continueTo(new PathChange("trunk/src", "trunk/AgreementMaker/src"));
        chain.tail().continueTo(new PathChange("trunk/README.txt", "trunk/AgreementMaker/README.txt"));
        chain.tail().continueTo(new PathChange("trunk/AMreminder", "trunk/AgreementMaker/AMreminder"));
        chain.tail().continueTo(new PathChange("trunk/look_and_feel", "trunk/AgreementMaker/look_and_feel"));
        chain.tail().continueTo(new PathChange("trunk/sounds", "trunk/AgreementMaker/sounds"));

        // other fixes, probably came from the svndumpfilter output
        chain.tail().continueTo(new NodeRemove(1843, "add", "branches"));
        chain.tail().continueTo(new NodeRemove(2875, "delete", "trunk/AgreementMaker/AM_ROOT"));

        chain.tail().continueTo(new NodeHeaderChange(2875, "add", "trunk/AgreementMaker-OSGi/AM_ROOT", SvnNodeHeader.COPY_FROM_REV, "2874", "2814"));

        chain.tail().continueTo(new UpdateAuthorForEmptyRevisions("cosmin"));

        SvnDumpValidator pathCollisionValidator = new PathCollision();
        SvnDumpValidator terminator = new TerminatingValidator(pathCollisionValidator);
        chain.tail().continueTo(terminator);

        // save the dump
        FileOutputStream fos = new FileOutputStream("/tmp/am_good.dump");
        SvnDumpWriter dumpWriter = new SvnDumpWriterImpl();
        dumpWriter.writeTo(fos);
        chain.tail().continueTo(dumpWriter);

        FileOutputStream summaryOs = new FileOutputStream("/tmp/am_good.summary");
        SvnDumpWriter summaryWriter = new SvnDumpSummary();
        summaryWriter.writeTo(summaryOs);
        chain.tail().continueTo(summaryWriter);

        final InputStream s = new FileInputStream("/home/cosmin/Desktop/AgreementMaker-GitHub-Conversion/onestep.dump");
        SvnDumpFileParser parser = new SvnDumpFileParser(new SvnDumpFileCharStream(s));

        parser.Start(chain);

        fos.close();
        summaryOs.close();
    }

    /**
     * I used this method to get a list of authors in the SVN dump file,
     * in order to pass the list to svn2git.
     */
    @Test
    @Ignore
    public void list_authors() throws IOException, ParseException {
        final InputStream s = new FileInputStream("/home/cosmin/Desktop/AgreementMaker-GitHub-Conversion/finished.dump");
        SvnDumpFileParser parser = new SvnDumpFileParser(new SvnDumpFileCharStream(s));
        SvnDumpWriter authorsWriter = new SvnDumpAuthors();
        authorsWriter.writeTo(System.out);

        parser.Start(authorsWriter);
    }
}
