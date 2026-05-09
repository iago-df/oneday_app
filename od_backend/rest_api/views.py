import json
import datetime as dt
from django.contrib.auth import authenticate
from django.http import JsonResponse
from django.utils.decorators import method_decorator
from django.views import View
from django.views.decorators.csrf import csrf_exempt
from django.contrib.auth.models import User

from .helpers import get_authenticated_user
from .models import AuthToken, UserProfile, Category, Tag, Goal, RecurrenceRule, ActivityTemplate


class AuthMixin:
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.user = None

    @method_decorator(csrf_exempt)
    def dispatch(self, request, *args, **kwargs):
        self.user, err = get_authenticated_user(request)
        if err:
            return err
        return super().dispatch(request, *args, **kwargs)


@method_decorator(csrf_exempt, name='dispatch')
class RegisterView(View):
    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        username = data.get('username', '').strip()
        email = data.get('email', '').strip()
        password = data.get('password', '')
        name = data.get('name', '').strip()

        if not username:
            return JsonResponse({'error': 'username is required'}, status=400)
        if not password:
            return JsonResponse({'error': 'password is required'}, status=400)

        if User.objects.filter(username=username).exists():
            return JsonResponse({'error': 'Username already taken'}, status=409)

        if email and User.objects.filter(email=email).exists():
            return JsonResponse({'error': 'Email already in use'}, status=409)

        user = User.objects.create_user(username=username, password=password, email=email)
        UserProfile.objects.create(user=user, name=name or username)

        token = AuthToken.objects.create(user=user, key=AuthToken.generate_key())

        return JsonResponse({
            'token': token.key,
            'user': {
                'id': user.id,
                'username': user.username,
                'email': user.email,
                'name': name or username,
            },
        }, status=201)


@method_decorator(csrf_exempt, name='dispatch')
class LoginView(View):
    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        username = data.get('username', '')
        password = data.get('password', '')

        if not username or not password:
            return JsonResponse({'error': 'username and password are required'}, status=400)

        user = authenticate(username=username, password=password)
        if not user:
            return JsonResponse({'error': 'Invalid credentials'}, status=401)

        token = AuthToken.objects.create(user=user, key=AuthToken.generate_key())

        profile = getattr(user, 'profile', None)

        return JsonResponse({
            'token': token.key,
            'user': {
                'id': user.id,
                'username': user.username,
                'email': user.email,
                'name': profile.name if profile else user.username,
            },
        })


class LogoutView(AuthMixin, View):
    def post(self, request):
        AuthToken.objects.filter(user=self.user, is_active=True).update(is_active=False)
        return JsonResponse({'message': 'Logged out'})


class MeView(AuthMixin, View):
    def get(self, request):
        try:
            profile = self.user.profile
            profile_data = {'name': profile.name, 'avatar_url': profile.avatar_url}
        except UserProfile.DoesNotExist:
            profile_data = {'name': self.user.username, 'avatar_url': None}

        return JsonResponse({
            'id': self.user.id,
            'username': self.user.username,
            'email': self.user.email,
            'name': profile_data['name'],
            'avatar_url': profile_data['avatar_url'],
        })


class ProfileView(AuthMixin, View):
    def _profile_json(self, profile):
        return {
            'id': profile.id,
            'username': self.user.username,
            'email': self.user.email,
            'name': profile.name,
            'avatar_url': profile.avatar_url,
            'created_at': profile.created_at.isoformat(),
            'updated_at': profile.updated_at.isoformat(),
        }

    def get(self, request):
        profile, _ = UserProfile.objects.get_or_create(user=self.user, defaults={'name': self.user.username})
        return JsonResponse(self._profile_json(profile))


    def put(self, request):
        profile, _ = UserProfile.objects.get_or_create(user=self.user, defaults={'name': self.user.username})

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'name' in data:
            name = data['name'].strip()
            if not name:
                return JsonResponse({'error': 'name cannot be empty'}, status=400)
            profile.name = name

        if 'avatar_url' in data:
            profile.avatar_url = data['avatar_url'] or None

        if 'email' in data:
            email = data['email'].strip()
            if email and User.objects.filter(email=email).exclude(id=self.user.id).exists():
                return JsonResponse({'error': 'Email already in use'}, status=409)
            self.user.email = email
            self.user.save(update_fields=['email'])

        profile.save()
        profile.refresh_from_db()
        return JsonResponse(self._profile_json(profile))



def _category_json(c):
    return {
        'id': c.id,
        'name': c.name,
        'icon': c.icon,
        'color': c.color,
        'is_active': c.is_active,
        'created_at': c.created_at.isoformat(),
        'updated_at': c.updated_at.isoformat(),
    }


class CategoriesListView(AuthMixin, View):
    def get(self, request):
        qs = Category.objects.filter(user=self.user).order_by('name')
        return JsonResponse({'categories': [_category_json(c) for c in qs]})


    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        name = data.get('name', '').strip()
        if not name:
            return JsonResponse({'error': 'name is required'}, status=400)

        if Category.objects.filter(user=self.user, name=name).exists():
            return JsonResponse({'error': 'Category with this name already exists'}, status=409)

        category = Category.objects.create(
            user=self.user,
            name=name,
            icon=data.get('icon') or None,
            color=data.get('color') or None,
            is_active=data.get('is_active', True),
        )
        return JsonResponse(_category_json(category), status=201)


class CategoriesDetailView(AuthMixin, View):
    def _get_category(self, id):
        try:
            return Category.objects.get(id=id, user=self.user), None
        except Category.DoesNotExist:
            return None, JsonResponse({'error': 'Category not found'}, status=404)

    def get(self, request, id):
        category, err = self._get_category(id)
        if err:
            return err
        return JsonResponse(_category_json(category))


    def put(self, request, id):
        category, err = self._get_category(id)
        if err:
            return err

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'name' in data:
            name = data['name'].strip()
            if not name:
                return JsonResponse({'error': 'name cannot be empty'}, status=400)
            if Category.objects.filter(user=self.user, name=name).exclude(id=id).exists():
                return JsonResponse({'error': 'Category with this name already exists'}, status=409)
            category.name = name

        if 'icon' in data:
            category.icon = data['icon'] or None
        if 'color' in data:
            category.color = data['color'] or None
        if 'is_active' in data:
            category.is_active = bool(data['is_active'])

        category.save()
        return JsonResponse(_category_json(category))


    def delete(self, request, id):
        category, err = self._get_category(id)
        if err:
            return err
        category.delete()
        return JsonResponse({'message': 'Category deleted'})




def _tag_json(t):
    return {
        'id': t.id,
        'name': t.name,
        'created_at': t.created_at.isoformat(),
        'updated_at': t.updated_at.isoformat(),
    }


class TagsListView(AuthMixin, View):
    def get(self, request):
        qs = Tag.objects.filter(user=self.user).order_by('name')
        return JsonResponse({'tags': [_tag_json(t) for t in qs]})


    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        name = data.get('name', '').strip()
        if not name:
            return JsonResponse({'error': 'name is required'}, status=400)

        if Tag.objects.filter(user=self.user, name=name).exists():
            return JsonResponse({'error': 'Tag with this name already exists'}, status=409)

        tag = Tag.objects.create(user=self.user, name=name)
        return JsonResponse(_tag_json(tag), status=201)


class TagsDetailView(AuthMixin, View):
    def _get_tag(self, id):
        try:
            return Tag.objects.get(id=id, user=self.user), None
        except Tag.DoesNotExist:
            return None, JsonResponse({'error': 'Tag not found'}, status=404)

    def get(self, request, id):
        tag, err = self._get_tag(id)
        if err:
            return err
        return JsonResponse(_tag_json(tag))

    def put(self, request, id):
        tag, err = self._get_tag(id)
        if err:
            return err

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'name' in data:
            name = data['name'].strip()
            if not name:
                return JsonResponse({'error': 'name cannot be empty'}, status=400)
            if Tag.objects.filter(user=self.user, name=name).exclude(id=id).exists():
                return JsonResponse({'error': 'Tag with this name already exists'}, status=409)
            tag.name = name

        tag.save()
        return JsonResponse(_tag_json(tag))


    def delete(self, request, id):
        tag, err = self._get_tag(id)
        if err:
            return err
        tag.delete()
        return JsonResponse({'message': 'Tag deleted'})





def _parse_date(value, field_name):
    if not value:
        return None, None
    try:
        return dt.date.fromisoformat(value), None
    except (TypeError, ValueError):
        return None, JsonResponse({'error': f'{field_name} must be YYYY-MM-DD'}, status=400)


def _goal_json(goal):
    return {
        'id': goal.id,
        'title': goal.title,
        'description': goal.description,
        'goal_type': goal.goal_type,
        'frequency': goal.frequency,
        'status': goal.status,
        'progress_percent': goal.progress_percent,
        'target_value': goal.target_value,
        'current_value': goal.current_value,
        'start_date': goal.start_date.isoformat() if goal.start_date else None,
        'end_date': goal.end_date.isoformat() if goal.end_date else None,
        'deadline': goal.deadline.isoformat() if goal.deadline else None,
        'is_active': goal.is_active,
        'category_id': goal.category_id,
        'parent_goal_id': goal.parent_goal_id,
        'tags': [{'id': t.id, 'name': t.name} for t in goal.tags.all()],
        'created_at': goal.created_at.isoformat(),
        'updated_at': goal.updated_at.isoformat(),
    }


def _resolve_tags(tag_ids, user):
    if not isinstance(tag_ids, list):
        return None, JsonResponse({'error': 'tag_ids must be a list'}, status=400)
    if not tag_ids:
        return [], None
    tags = list(Tag.objects.filter(id__in=tag_ids, user=user))
    if len(tags) != len(set(tag_ids)):
        return None, JsonResponse({'error': 'One or more tag IDs not found'}, status=404)
    return tags, None



class GoalsListView(AuthMixin, View):
    def get(self, request):
        qs = Goal.objects.filter(user=self.user).prefetch_related('tags').order_by('-created_at')

        goal_type = request.GET.get('goal_type')
        category_id = request.GET.get('category_id')
        status = request.GET.get('status')
        from_date = request.GET.get('from')
        to_date = request.GET.get('to')

        if goal_type:
            qs = qs.filter(goal_type=goal_type)
        if category_id:
            qs = qs.filter(category_id=category_id)
        if status:
            qs = qs.filter(status=status)
        if from_date:
            qs = qs.filter(start_date__gte=from_date)
        if to_date:
            qs = qs.filter(deadline__lte=to_date)

        return JsonResponse({'goals': [_goal_json(g) for g in qs]})


    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        title = data.get('title', '').strip()
        if not title:
            return JsonResponse({'error': 'title is required'}, status=400)

        progress_percent = data.get('progress_percent', 0)
        try:
            progress_percent = float(progress_percent)
        except (TypeError, ValueError):
            return JsonResponse({'error': 'progress_percent must be a number'}, status=400)
        if not (0 <= progress_percent <= 100):
            return JsonResponse({'error': 'progress_percent must be between 0 and 100'}, status=400)

        category = None
        if data.get('category_id'):
            try:
                category = Category.objects.get(id=data['category_id'], user=self.user)
            except Category.DoesNotExist:
                return JsonResponse({'error': 'Category not found'}, status=404)

        parent_goal = None
        if data.get('parent_goal_id'):
            try:
                parent_goal = Goal.objects.get(id=data['parent_goal_id'], user=self.user)
            except Goal.DoesNotExist:
                return JsonResponse({'error': 'Parent goal not found'}, status=404)

        start_date, err = _parse_date(data.get('start_date'), 'start_date')
        if err:
            return err

        end_date, err = _parse_date(data.get('end_date'), 'end_date')
        if err:
            return err

        deadline, err = _parse_date(data.get('deadline'), 'deadline')
        if err:
            return err


        tags, err = _resolve_tags(data.get('tag_ids', []), self.user)
        if err:
            return err

        goal = Goal.objects.create(
            user=self.user,
            title=title,
            description=data.get('description') or None,
            goal_type=data.get('goal_type', 'daily'),
            frequency=data.get('frequency', 'once'),
            status=data.get('status', 'planned'),
            progress_percent=progress_percent,
            target_value=data.get('target_value') or None,
            current_value=data.get('current_value') or None,
            start_date=start_date,
            end_date=end_date,
            deadline=deadline,
            is_active=data.get('is_active', True),
            category=category,
            parent_goal=parent_goal,
        )
        if tags:
            goal.tags.set(tags)

        return JsonResponse(_goal_json(goal), status=201)



class GoalsDetailView(AuthMixin, View):
    def _get_goal(self, id):
        try:
            return Goal.objects.prefetch_related('tags').get(id=id, user=self.user), None
        except Goal.DoesNotExist:
            return None, JsonResponse({'error': 'Goal not found'}, status=404)

    def get(self, request, id):
        goal, err = self._get_goal(id)
        if err:
            return err
        return JsonResponse(_goal_json(goal))


    def put(self, request, id):
        goal, err = self._get_goal(id)
        if err:
            return err

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'title' in data:
            title = data['title'].strip()
            if not title:
                return JsonResponse({'error': 'title cannot be empty'}, status=400)
            goal.title = title

        if 'progress_percent' in data:
            try:
                progress_percent = float(data['progress_percent'])
            except (TypeError, ValueError):
                return JsonResponse({'error': 'progress_percent must be a number'}, status=400)
            if not (0 <= progress_percent <= 100):
                return JsonResponse({'error': 'progress_percent must be between 0 and 100'}, status=400)
            goal.progress_percent = progress_percent

        if 'category_id' in data:
            if data['category_id'] is None:
                goal.category = None
            else:
                try:
                    goal.category = Category.objects.get(id=data['category_id'], user=self.user)
                except Category.DoesNotExist:
                    return JsonResponse({'error': 'Category not found'}, status=404)

        if 'parent_goal_id' in data:
            if data['parent_goal_id'] is None:
                goal.parent_goal = None
            else:
                if data['parent_goal_id'] == goal.id:
                    return JsonResponse({'error': 'A goal cannot be its own parent'}, status=400)
                try:
                    goal.parent_goal = Goal.objects.get(id=data['parent_goal_id'], user=self.user)
                except Goal.DoesNotExist:
                    return JsonResponse({'error': 'Parent goal not found'}, status=404)

        simple_fields = (
            'description', 'goal_type', 'frequency', 'status',
            'target_value', 'current_value', 'start_date',
            'end_date', 'deadline', 'is_active',
        )
        for field in simple_fields:
            if field in data:
                setattr(goal, field, data[field] if data[field] != '' else None)

        goal.save()

        if 'tag_ids' in data:
            tags, err = _resolve_tags(data['tag_ids'], self.user)
            if err:
                return err
            goal.tags.set(tags)

        goal.refresh_from_db()
        return JsonResponse(_goal_json(goal))


    def delete(self, request, id):
        goal, err = self._get_goal(id)
        if err:
            return err
        goal.delete()
        return JsonResponse({'message': 'Goal deleted'})





_VALID_FREQUENCIES = {'none', 'daily', 'weekdays', 'weekly', 'monthly', 'custom'}
_VALID_DAYS = {'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'}


def _validate_days_of_week(days):
    if not isinstance(days, list):
        return 'days_of_week must be a list'
    invalid = [d for d in days if not isinstance(d, str) or d not in _VALID_DAYS]
    if invalid:
        return f'Invalid days_of_week values: {invalid}. Allowed: {sorted(_VALID_DAYS)}'
    return None


def _safe_date(val):
    if val is None:
        return None
    return val if isinstance(val, str) else val.isoformat()


def _recurrence_rule_json(rule):
    return {
        'id': rule.id,
        'frequency': rule.frequency,
        'interval': rule.interval,
        'days_of_week': rule.days_of_week,
        'day_of_month': rule.day_of_month,
        'start_date': _safe_date(rule.start_date),
        'end_date': _safe_date(rule.end_date),
        'is_active': rule.is_active,
        'created_at': rule.created_at.isoformat(),
        'updated_at': rule.updated_at.isoformat(),
    }


class RecurrenceRulesListView(AuthMixin, View):
    def get(self, request):
        qs = RecurrenceRule.objects.filter(user=self.user).order_by('-created_at')
        return JsonResponse({'recurrence_rules': [_recurrence_rule_json(r) for r in qs]})

    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        frequency = data.get('frequency', 'none')
        if frequency not in _VALID_FREQUENCIES:
            return JsonResponse({'error': f'Invalid frequency. Allowed: {sorted(_VALID_FREQUENCIES)}'}, status=400)

        interval = data.get('interval', 1)
        try:
            interval = int(interval)
            if interval < 1:
                raise ValueError
        except (TypeError, ValueError):
            return JsonResponse({'error': 'interval must be a positive integer'}, status=400)

        days_of_week = data.get('days_of_week')
        if days_of_week is not None:
            err_msg = _validate_days_of_week(days_of_week)
            if err_msg:
                return JsonResponse({'error': err_msg}, status=400)

        rule = RecurrenceRule.objects.create(
            user=self.user,
            frequency=frequency,
            interval=interval,
            days_of_week=days_of_week,
            day_of_month=data.get('day_of_month') or None,
            start_date=data.get('start_date') or None,
            end_date=data.get('end_date') or None,
            is_active=data.get('is_active', True),
        )
        return JsonResponse(_recurrence_rule_json(rule), status=201)




class RecurrenceRulesDetailView(AuthMixin, View):
    def _get_rule(self, id):
        try:
            return RecurrenceRule.objects.get(id=id, user=self.user), None
        except RecurrenceRule.DoesNotExist:
            return None, JsonResponse({'error': 'RecurrenceRule not found'}, status=404)

    def get(self, request, id):
        rule, err = self._get_rule(id)
        if err:
            return err
        return JsonResponse(_recurrence_rule_json(rule))


    def put(self, request, id):
        rule, err = self._get_rule(id)
        if err:
            return err

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'frequency' in data:
            if data['frequency'] not in _VALID_FREQUENCIES:
                return JsonResponse({'error': f'Invalid frequency. Allowed: {sorted(_VALID_FREQUENCIES)}'}, status=400)
            rule.frequency = data['frequency']

        if 'interval' in data:
            try:
                interval = int(data['interval'])
                if interval < 1:
                    raise ValueError
            except (TypeError, ValueError):
                return JsonResponse({'error': 'interval must be a positive integer'}, status=400)
            rule.interval = interval

        if 'days_of_week' in data:
            if data['days_of_week'] is None:
                rule.days_of_week = None
            else:
                err_msg = _validate_days_of_week(data['days_of_week'])
                if err_msg:
                    return JsonResponse({'error': err_msg}, status=400)
                rule.days_of_week = data['days_of_week']

        for field in ('day_of_month', 'start_date', 'end_date', 'is_active'):
            if field in data:
                setattr(rule, field, data[field] if data[field] != '' else None)

        rule.save()
        return JsonResponse(_recurrence_rule_json(rule))


    def delete(self, request, id):
        rule, err = self._get_rule(id)
        if err:
            return err
        rule.delete()
        return JsonResponse({'message': 'RecurrenceRule deleted'})





_VALID_ACTIVITY_TYPES = {'task', 'session', 'habit', 'event', 'deep_work'}


def _activity_template_json(template):
    return {
        'id': template.id,
        'title': template.title,
        'description': template.description,
        'activity_type': template.activity_type,
        'estimated_minutes': template.estimated_minutes,
        'is_active': template.is_active,
        'category_id': template.category_id,
        'recurrence_rule_id': template.recurrence_rule_id,
        'recurrence_rule': _recurrence_rule_json(template.recurrence_rule) if template.recurrence_rule else None,
        'created_at': template.created_at.isoformat(),
        'updated_at': template.updated_at.isoformat(),
    }


def _resolve_template_fks(data, user, current_category=None, current_rule=None):
    category = current_category
    rule = current_rule

    if 'category_id' in data:
        if data['category_id'] is None:
            category = None
        else:
            try:
                category = Category.objects.get(id=data['category_id'], user=user)
            except Category.DoesNotExist:
                return None, None, JsonResponse({'error': 'Category not found'}, status=404)

    if 'recurrence_rule_id' in data:
        if data['recurrence_rule_id'] is None:
            rule = None
        else:
            try:
                rule = RecurrenceRule.objects.get(id=data['recurrence_rule_id'], user=user)
            except RecurrenceRule.DoesNotExist:
                return None, None, JsonResponse({'error': 'RecurrenceRule not found'}, status=404)

    return category, rule, None


class ActivityTemplatesListView(AuthMixin, View):
    def get(self, request):
        qs = (ActivityTemplate.objects
              .filter(user=self.user)
              .select_related('recurrence_rule')
              .order_by('-created_at'))
        return JsonResponse({'activity_templates': [_activity_template_json(t) for t in qs]})


    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        title = data.get('title', '').strip()
        if not title:
            return JsonResponse({'error': 'title is required'}, status=400)

        activity_type = data.get('activity_type', 'task')
        if activity_type not in _VALID_ACTIVITY_TYPES:
            return JsonResponse({'error': f'Invalid activity_type. Allowed: {sorted(_VALID_ACTIVITY_TYPES)}'}, status=400)

        estimated_minutes = data.get('estimated_minutes')
        if estimated_minutes is not None:
            try:
                estimated_minutes = int(estimated_minutes)
                if estimated_minutes < 1:
                    raise ValueError
            except (TypeError, ValueError):
                return JsonResponse({'error': 'estimated_minutes must be a positive integer'}, status=400)

        category, rule, err = _resolve_template_fks(data, self.user)
        if err:
            return err

        template = ActivityTemplate.objects.create(
            user=self.user,
            title=title,
            description=data.get('description') or None,
            activity_type=activity_type,
            estimated_minutes=estimated_minutes,
            is_active=data.get('is_active', True),
            category=category,
            recurrence_rule=rule,
        )
        template = ActivityTemplate.objects.select_related('recurrence_rule').get(id=template.id)
        return JsonResponse(_activity_template_json(template), status=201)



class ActivityTemplatesDetailView(AuthMixin, View):
    def _get_template(self, id):
        try:
            return (ActivityTemplate.objects
                    .select_related('recurrence_rule')
                    .get(id=id, user=self.user)), None
        except ActivityTemplate.DoesNotExist:
            return None, JsonResponse({'error': 'ActivityTemplate not found'}, status=404)

    def get(self, request, id):
        template, err = self._get_template(id)
        if err:
            return err
        return JsonResponse(_activity_template_json(template))


    def put(self, request, id):
        template, err = self._get_template(id)
        if err:
            return err

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'title' in data:
            title = data['title'].strip()
            if not title:
                return JsonResponse({'error': 'title cannot be empty'}, status=400)
            template.title = title

        if 'activity_type' in data:
            if data['activity_type'] not in _VALID_ACTIVITY_TYPES:
                return JsonResponse({'error': f'Invalid activity_type. Allowed: {sorted(_VALID_ACTIVITY_TYPES)}'}, status=400)
            template.activity_type = data['activity_type']

        if 'estimated_minutes' in data:
            if data['estimated_minutes'] is None:
                template.estimated_minutes = None
            else:
                try:
                    em = int(data['estimated_minutes'])
                    if em < 1:
                        raise ValueError
                except (TypeError, ValueError):
                    return JsonResponse({'error': 'estimated_minutes must be a positive integer'}, status=400)
                template.estimated_minutes = em

        category, rule, err = _resolve_template_fks(
            data, self.user,
            current_category=template.category,
            current_rule=template.recurrence_rule,
        )
        if err:
            return err
        template.category = category
        template.recurrence_rule = rule

        if 'description' in data:
            template.description = data['description'] or None
        if 'is_active' in data:
            template.is_active = bool(data['is_active'])

        template.save()
        template.refresh_from_db()
        return JsonResponse(_activity_template_json(template))


    def delete(self, request, id):
        template, err = self._get_template(id)
        if err:
            return err
        template.delete()
        return JsonResponse({'message': 'ActivityTemplate deleted'})
