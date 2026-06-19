package net.kikin.nubecita.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AvatarGroupSplitTest {
    @Test
    fun `under the cap shows all avatars and no overflow`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 3, overflowCount = 0), avatarGroupSplit(total = 3, maxVisible = 4))
    }

    @Test
    fun `exactly at the cap shows all avatars and no overflow`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 4, overflowCount = 0), avatarGroupSplit(total = 4, maxVisible = 4))
    }

    @Test
    fun `over the cap shows maxVisible avatars plus the remainder as overflow`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 4, overflowCount = 2), avatarGroupSplit(total = 6, maxVisible = 4))
    }

    @Test
    fun `empty is empty`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 0, overflowCount = 0), avatarGroupSplit(total = 0, maxVisible = 4))
    }

    @Test
    fun `maxVisible of zero shows no avatars and all as overflow`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 0, overflowCount = 5), avatarGroupSplit(total = 5, maxVisible = 0))
    }

    @Test
    fun `negative maxVisible is coerced to zero, not crashing`() {
        assertEquals(AvatarGroupSplit(visibleAvatars = 0, overflowCount = 5), avatarGroupSplit(total = 5, maxVisible = -1))
    }
}
