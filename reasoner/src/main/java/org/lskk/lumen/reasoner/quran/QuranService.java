package org.lskk.lumen.reasoner.quran;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.lskk.lumen.core.AudioObject;
import org.lskk.lumen.core.CommunicateAction;
import org.lskk.lumen.reasoner.activity.Scriptable;
import org.lskk.lumen.reasoner.ux.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Locale;

/**
 * logic utama dari aplikasi quran
 * Created by ceefour on 29/11/2015.
 */
@Service @Scriptable
public class QuranService {
    
    private static final Logger log = LoggerFactory.getLogger(QuranService.class);

    @Inject
    private Environment env;
    @Inject
    private QuranChapterRepository quranChapterRepo;
    @Inject
    private QuranVerseRepository quranVerseRepo;
    @Inject
    private LiteralRepository literalRepo;

    public void resolve(ReciteQuran reciteQuran) {
        try {
            reciteQuran.setChapterNumber(Integer.parseInt(reciteQuran.getUpChapter()));
        } catch (NumberFormatException e) {
            final Integer chapterNum = quranChapterRepo.getChapterNumByFuzzy(reciteQuran.getUpChapter());
            reciteQuran.setChapterNumber(chapterNum);
        }
        reciteQuran.setVerseNumbers(ImmutableList.of(Integer.parseInt(reciteQuran.getUpVerses())));
    }

    public CommunicateAction recite(String upChapter, int upVerse, @Nullable Channel<?> channel) {
        return recite(new ReciteQuran(upChapter, upVerse, channel));
    }
    
    public CommunicateAction recite(ReciteQuran reciteQuran) {
        try {
            log.info("I want to recite chapter {} verse {}", reciteQuran.getUpChapter(), reciteQuran.getUpVerses());
            resolve(reciteQuran);
            log.info("I want to see chapter {} verse {}", reciteQuran.getChapterNumber(), reciteQuran.getVerseNumbers());
            final String quranChapterId = "quran_" + reciteQuran.getChapterNumber();
            QuranChapter quranChapter = Preconditions.checkNotNull(
                    quranChapterRepo.findOne(quranChapterId),
                    "Cannot find QuranChapter '%s'", quranChapterId);
            log.info("I want to see quran chapter {}", quranChapter);

            final String quranChapterAndVerseId = "quran_" + reciteQuran.getChapterNumber() + "_verse_" + reciteQuran.getVerseNumbers().get(0);
            final QuranVerse quranVerse = Preconditions.checkNotNull(quranVerseRepo.findOne(quranChapterAndVerseId),
                    "Cannot find QuranVerse '%s'", quranChapterAndVerseId);

            log.info("I want to see quran chapter and verse id {} quran verse {}", quranChapterAndVerseId, quranVerse);
            final String literalId = "quran_" + reciteQuran.getChapterNumber() + "_verse_" + reciteQuran.getVerseNumbers().get(0) + "_ind";

            final Literal literal = Preconditions.checkNotNull(literalRepo.findOne(literalId),
                    "Cannot find Literal '%s'", literalId);
            log.info("I want to see literal id {} Literal{}", literalId, literal);

            final CommunicateAction communicateAction = new CommunicateAction();
            //communicateAction.setAvatarId(reciteQuran.avatarId);
            communicateAction.setInLanguage(Locale.forLanguageTag("id-ID"));
            communicateAction.setObject("Surat " + quranChapter.getNameWithTashkeel() + " Ayat " + quranVerse.getVerseNum() + " : " + literal.getAdoc());
            communicateAction.setUsedForSynthesis(true);
            final AudioObject audioObject = new AudioObject();
            final String audioUrl = new java.io.File(
                    String.format("%s/%03d%03d.ogg", env.getRequiredProperty("quran.recitation.folder"),
                            reciteQuran.getChapterNumber(), reciteQuran.getVerseNumbers().get(0))).toURI().toString();
            audioObject.setUrl(audioUrl);
            log.info("audioUrl={}", audioUrl);
            communicateAction.setAudio(audioObject);

            if (null != reciteQuran.getChannel()) {
                reciteQuran.getChannel().express(reciteQuran.getAvatarId(), communicateAction, null);
            }
            return communicateAction;
        } catch (Exception e) {
            log.error("Cannot recite " + reciteQuran, e);
        }
        return null;
    }
}
