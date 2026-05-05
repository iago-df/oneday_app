from django.contrib import admin
from .models import (
    AuthToken,
    UserProfile,
    Category,
    Tag,
    Goal,
    DayEntry,
    RecurrenceRule,
    ActivityTemplate,
    Activity,
    DayNote,
)

admin.site.register(AuthToken)
admin.site.register(UserProfile)
admin.site.register(Category)
admin.site.register(Tag)
admin.site.register(Goal)
admin.site.register(DayEntry)
admin.site.register(RecurrenceRule)
admin.site.register(ActivityTemplate)
admin.site.register(Activity)
admin.site.register(DayNote)
