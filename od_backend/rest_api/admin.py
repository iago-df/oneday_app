from django.contrib import admin
from .models import (
    AuthToken,
    UserProfile,
    Goal,
    DayEntry,
    Activity,
)

admin.site.register(AuthToken)
admin.site.register(UserProfile)
admin.site.register(Goal)
admin.site.register(DayEntry)
admin.site.register(Activity)
