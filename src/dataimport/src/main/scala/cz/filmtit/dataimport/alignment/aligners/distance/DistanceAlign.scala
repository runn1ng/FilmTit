package cz.filmtit.dataimport.alignment.aligners.distance

import cz.filmtit.core.Configuration
import cz.filmtit.share.Language
import cz.filmtit.dataimport.SubtitleMapping
import  cz.filmtit.dataimport.alignment.model.Aligner

/**
 * Object with main class for making a distance-based alignment
 */
object DistanceAlign {


  /**
   * Run the distance-based linear aligning
   * @param args
   */
    def main(args: Array[String]) ={
        val config = new Configuration("configuration.xml")
        val counter = new LinearFilePairCounter

        val aligner = new Aligner(
            new DistanceSubtitleFileAlignment(Language.EN, Language.CS, counter),
            new DistanceChunkAlignment(Language.EN, Language.CS, counter),
            new DistanceGoodFilePairChooser(counter),
            config
        )

        aligner.align(new SubtitleMapping(config));
    }
}
