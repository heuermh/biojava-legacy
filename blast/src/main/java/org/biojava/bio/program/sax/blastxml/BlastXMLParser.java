/*
 *                    BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors.  These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 *      http://www.biojava.org/
 *
 */

package org.biojava.bio.program.sax.blastxml;

import org.biojava.bio.seq.io.game.ElementRecognizer;
import org.biojava.utils.stax.DelegationManager;
import org.biojava.utils.stax.StAXContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class parses NCBI Blast XML output.
 * <p>
 * It has two modes:-
 * i) single output document mode: this takes a document
 * containing a single BlastOutput element and parses it.
 * This is generated when a single query is searched against
 * a sequence database.
 * <p>
 * ii) multiple query document mode: unfortunately, NCBI
 * BLAST concatenates the results of multiple searches in
 * one file.  This leads to an ill-formed document that violates
 * every XML format known to the human race and other nearby 
 * civilisations.  This parser will take a bowdlerised version of
 * this output that is wrapped in a blast_aggregate element.
 * <p>
 * The massaged form is generated by stripping the XML element and
 * DOCTYPE elements and wrapping all the classes in a single
 * blast_aggregate element.  In Linux, this can be done with:-
 * <pre>
 * #!/bin/sh
 * # Converts a Blast XML output to something vaguely well-formed
 * # for parsing.
 * # Use: blast_aggregate <XML output> <editted file>
 *
 * # strips all &lt;?xml&gt; and &lt;!DOCTYPE&gt; tags
 * # encapsulates the multiple &lt;BlastOutput&gt; elements into &lt;blast_aggregator&gt;
 *
 * sed '/&gt;?xml/d' $1 | sed '/&lt;!DOCTYPE/d' | sed '1i\
 * &lt;blast_aggregate&gt;
 * $a\
 * &lt;/blast_aggregate&gt;' > $2
 *</pre>

 * @author David Huen
 */
public class BlastXMLParser
    extends StAXFeatureHandler
{
    boolean firstTime = true;

    // constructor
    public BlastXMLParser()
    {
        // this is the base element class
        this.staxenv = this;
//        System.out.println("staxenv " + staxenv);
        // just set a DefaultHandler: does nothing worthwhile.
        this.listener = new DefaultHandler();
    }

    /**
     * sets the ContentHandler for this object
     */
    public void setContentHandler(org.xml.sax.ContentHandler listener)
    {
        this.listener = listener;
    }

    /**
     * we override the superclass startElement method so we can determine the
     * the start tag type and use it to set up delegation for the superclass.
     */
    public void startElement(
            String nsURI,
            String localName,
            String qName,
            Attributes attrs,
            DelegationManager dm)
        throws SAXException
    {
//        System.out.println("localName is " + localName);
        if (firstTime) {
            // what kind of tag do we have?
            if (localName.equals("BlastOutput")) {
                // this is a well-formed XML document from NCBI BLAST
                // pertaining to one search result
                super.addHandler(
                    new ElementRecognizer.ByLocalName("BlastOutput"),
                    new StAXHandlerFactory() {
                        public StAXContentHandler getHandler(StAXFeatureHandler staxenv) {
                            return new BlastOutputHandler(staxenv);
                        }
                    }
                );
            }
            else if (localName.equals("blast_aggregate")) {
                // this is my phony aggregate document that exists to
                // legitimise otherwise ill-formed output from NCBI Blast
                super.addHandler(new ElementRecognizer.ByLocalName("blast_aggregate"),
                    new StAXHandlerFactory() {
                        public StAXContentHandler getHandler(StAXFeatureHandler staxenv) {
                            return new BlastAggregator(staxenv);
                        }
                    }
                );
            }
            else {
                throw new SAXException("illegal element " + localName);
            }

            firstTime = false;

            // setup the root element of the output
            AttributesImpl bldscAttrs = new AttributesImpl();
            bldscAttrs.addAttribute("", "xmlns", "xmlns", CDATA, "");
            bldscAttrs.addAttribute(biojavaUri, "biojava", "xmlns:biojava", CDATA, "http://www.biojava.org");
            listener.startElement(biojavaUri, "BlastLikeDataSetCollection", biojavaUri + ":BlastLikeDataSetCollection", bldscAttrs);
        }

        // now invoke delegation
//        super.startElement(nsURI, localName, qName, attrs, dm);

        level++;

        // perform delegation
        // we must delegate only on features that are directly attached.
        // if I do not check that that's so, any element of a kind I delegate
        // on will be detected any depth within unrecognized tags.
        if (level == 1) {
//        System.out.println("StaxFeaturehandler.startElement starting. localName: " + localName + " " + level);
            for (int i = handlers.size() - 1; i >= 0; --i) {
                Binding b = (Binding) handlers.get(i);
                if (b.recognizer.filterStartElement(nsURI, localName, qName, attrs)) {
                    dm.delegate(b.handlerFactory.getHandler(this));
                    return;
                }
            }
        }

        // call the element specific handler now.
        // remember that if we we have a delegation failure we pass here too!
        if (level == 1) {
            startElementHandler(nsURI, localName, qName, attrs);
        }
    }

    public void endElementHandler(
            String nsURI,
            String localName,
            String qName,
            StAXContentHandler handler)
             throws SAXException
    {
        listener.endElement(biojavaUri, "BlastLikeDataSetCollection", biojavaUri + ":BlastLikeDataSetCollection");
    }
}
