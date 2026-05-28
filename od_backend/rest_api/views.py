import json
import datetime as dt
from django.contrib.auth import authenticate
from django.http import JsonResponse
from django.utils.decorators import method_decorator
from django.views import View
from django.views.decorators.csrf import csrf_exempt
from django.contrib.auth.models import User
from django.utils import timezone

from .helpers import get_authenticated_user
from .models import AuthToken, UserProfile, Goal, DayEntry, Activity


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
        'created_at': goal.created_at.isoformat(),
        'updated_at': goal.updated_at.isoformat(),
    }





class GoalsListView(AuthMixin, View):
    def get(self, request):
        qs = Goal.objects.filter(user=self.user).order_by('-created_at')

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


        start_date, err = _parse_date(data.get('start_date'), 'start_date')
        if err:
            return err

        end_date, err = _parse_date(data.get('end_date'), 'end_date')
        if err:
            return err

        deadline, err = _parse_date(data.get('deadline'), 'deadline')
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
        )

        return JsonResponse(_goal_json(goal), status=201)



class GoalsDetailView(AuthMixin, View):
    def _get_goal(self, id):
        try:
            return Goal.objects.get(id=id, user=self.user), None
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


        simple_fields = (
            'description', 'goal_type', 'frequency', 'status',
            'target_value', 'current_value', 'start_date',
            'end_date', 'deadline', 'is_active',
        )
        for field in simple_fields:
            if field in data:
                setattr(goal, field, data[field] if data[field] != '' else None)

        goal.save()

        return JsonResponse(_goal_json(goal))


    def delete(self, request, id):
        goal, err = self._get_goal(id)
        if err:
            return err
        from django.db import connection
        with connection.cursor() as cursor:
            cursor.execute('PRAGMA foreign_keys=OFF')
        try:
            goal.delete()
        finally:
            with connection.cursor() as cursor:
                cursor.execute('PRAGMA foreign_keys=ON')
        return JsonResponse({'message': 'Goal deleted'})




_VALID_ACTIVITY_TYPES = {'task', 'session', 'habit', 'event', 'deep_work'}

def _main_goal_summary(goal):
    if not goal:
        return None
    return {
        'id': goal.id,
        'title': goal.title,
        'status': goal.status,
        'goal_type': goal.goal_type,
        'progress_percent': goal.progress_percent,
        'deadline': goal.deadline.isoformat() if goal.deadline else None,
    }


def _day_entry_json(entry):
    return {
        'id': entry.id,
        'date': entry.date.isoformat(),
        'status': entry.status,
        'progress_percent': entry.progress_percent,
        'dedication_minutes': entry.dedication_minutes,
        'result_text': entry.result_text,
        'reflection_text': entry.reflection_text,
        'failure_reason': entry.failure_reason,
        'is_closed': entry.is_closed,
        'closed_at': entry.closed_at.isoformat() if entry.closed_at else None,
        'main_goal_id': entry.main_goal_id,
        'main_goal': _main_goal_summary(entry.main_goal),
        'created_at': entry.created_at.isoformat(),
        'updated_at': entry.updated_at.isoformat(),
    }


def _apply_close_fields(entry, data, close=False):
    if 'status' in data:
        entry.status = data['status']
    elif close and not entry.is_closed:
        entry.status = 'completed'

    if 'progress_percent' in data:
        try:
            pct = float(data['progress_percent'])
        except (TypeError, ValueError):
            return JsonResponse({'error': 'progress_percent must be a number'}, status=400)
        if not (0 <= pct <= 100):
            return JsonResponse({'error': 'progress_percent must be between 0 and 100'}, status=400)
        entry.progress_percent = pct
    elif close and entry.status == 'completed':
        entry.progress_percent = 100

    for field in ('result_text', 'reflection_text', 'failure_reason', 'dedication_minutes'):
        if field in data:
            entry.__setattr__(field, data[field] if data[field] != '' else None)

    if close:
        entry.is_closed = True
        entry.closed_at = timezone.now()

    return None



class DayEntriesListView(AuthMixin, View):
    def get(self, request):
        qs = DayEntry.objects.filter(user=self.user).select_related('main_goal').order_by('-date')

        date_exact = request.GET.get('date')
        from_date = request.GET.get('from')
        to_date = request.GET.get('to')
        status = request.GET.get('status')

        if date_exact:
            qs = qs.filter(date=date_exact)
        if from_date:
            qs = qs.filter(date__gte=from_date)
        if to_date:
            qs = qs.filter(date__lte=to_date)
        if status:
            qs = qs.filter(status=status)

        return JsonResponse({'day_entries': [_day_entry_json(e) for e in qs]})


    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        date_str = data.get('date', '').strip()
        if not date_str:
            return JsonResponse({'error': 'date is required'}, status=400)

        if DayEntry.objects.filter(user=self.user, date=date_str).exists():
            return JsonResponse({'error': 'A DayEntry for this date already exists'}, status=409)

        main_goal = None
        if data.get('main_goal_id'):
            try:
                main_goal = Goal.objects.get(id=data['main_goal_id'], user=self.user)
            except Goal.DoesNotExist:
                return JsonResponse({'error': 'Goal not found'}, status=404)

        status_val = data.get('status', 'empty')
        progress_percent = data.get('progress_percent', 0)
        if status_val == 'completed' and 'progress_percent' not in data:
            progress_percent = 100
        else:
            try:
                progress_percent = float(progress_percent)
            except (TypeError, ValueError):
                return JsonResponse({'error': 'progress_percent must be a number'}, status=400)
            if not (0 <= progress_percent <= 100):
                return JsonResponse({'error': 'progress_percent must be between 0 and 100'}, status=400)

        try:
            entry = DayEntry.objects.create(
                user=self.user,
                date=date_str,
                main_goal=main_goal,
                status=status_val,
                progress_percent=progress_percent,
                dedication_minutes=data.get('dedication_minutes') or None,
                result_text=data.get('result_text') or None,
                reflection_text=data.get('reflection_text') or None,
                failure_reason=data.get('failure_reason') or None,
            )
        except Exception:
            return JsonResponse({'error': 'Invalid date format. Use YYYY-MM-DD'}, status=400)

        entry = DayEntry.objects.select_related('main_goal').get(id=entry.id)
        return JsonResponse(_day_entry_json(entry), status=201)



class DayEntriesTodayView(AuthMixin, View):
    def get(self, request):
        today = timezone.now().date()
        entry, _ = DayEntry.objects.get_or_create(
            user=self.user,
            date=today,
            defaults={'status': 'empty'},
        )
        entry = DayEntry.objects.select_related('main_goal').get(id=entry.id)
        return JsonResponse(_day_entry_json(entry))



class DayEntriesItemView(AuthMixin, View):
    def _get_entry(self, id):
        try:
            return DayEntry.objects.select_related('main_goal').get(id=id, user=self.user), None
        except DayEntry.DoesNotExist:
            return None, JsonResponse({'error': 'DayEntry not found'}, status=404)

    def get(self, request, id):
        entry, err = self._get_entry(id)
        if err:
            return err
        return JsonResponse(_day_entry_json(entry))


    def put(self, request, id):
        entry, err = self._get_entry(id)
        if err:
            return err

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'main_goal_id' in data:
            if data['main_goal_id'] is None:
                entry.main_goal = None
            else:
                try:
                    entry.main_goal = Goal.objects.get(id=data['main_goal_id'], user=self.user)
                except Goal.DoesNotExist:
                    return JsonResponse({'error': 'Goal not found'}, status=404)

        if 'progress_percent' in data:
            try:
                pct = float(data['progress_percent'])
            except (TypeError, ValueError):
                return JsonResponse({'error': 'progress_percent must be a number'}, status=400)
            if not (0 <= pct <= 100):
                return JsonResponse({'error': 'progress_percent must be between 0 and 100'}, status=400)
            entry.progress_percent = pct

        if 'status' in data:
            entry.status = data['status']
            if data['status'] == 'completed' and 'progress_percent' not in data and entry.progress_percent == 0:
                entry.progress_percent = 100

        for field in ('dedication_minutes', 'result_text', 'reflection_text', 'failure_reason'):
            if field in data:
                entry.__setattr__(field, data[field] if data[field] != '' else None)

        entry.save()
        return JsonResponse(_day_entry_json(entry))


    def delete(self, request, id):
        entry, err = self._get_entry(id)
        if err:
            return err
        entry.delete()
        return JsonResponse({'message': 'DayEntry deleted'})



class DayEntriesCloseView(AuthMixin, View):
    def put(self, request, id):
        try:
            entry = DayEntry.objects.select_related('main_goal').get(id=id, user=self.user)
        except DayEntry.DoesNotExist:
            return JsonResponse({'error': 'DayEntry not found'}, status=404)

        if entry.is_closed:
            return JsonResponse({'error': 'DayEntry is already closed'}, status=409)

        try:
            data = json.loads(request.body) if request.body else {}
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        err_resp = _apply_close_fields(entry, data, close=True)
        if err_resp:
            return err_resp

        entry.save()
        return JsonResponse(_day_entry_json(entry))



class DayEntriesDraftCloseView(AuthMixin, View):
    def put(self, request, id):
        try:
            entry = DayEntry.objects.select_related('main_goal').get(id=id, user=self.user)
        except DayEntry.DoesNotExist:
            return JsonResponse({'error': 'DayEntry not found'}, status=404)

        if entry.is_closed:
            return JsonResponse({'error': 'DayEntry is already closed'}, status=409)

        try:
            data = json.loads(request.body) if request.body else {}
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        err_resp = _apply_close_fields(entry, data, close=False)
        if err_resp:
            return err_resp

        entry.save()
        return JsonResponse(_day_entry_json(entry))






_VALID_STATUSES = {'pending', 'in_progress', 'completed', 'partial', 'failed'}

def _safe_time(val):
    if val is None:
        return None
    return val if isinstance(val, str) else val.isoformat()


def _activity_json(activity):
    return {
        'id': activity.id,
        'title': activity.title,
        'description': activity.description,
        'activity_type': activity.activity_type,
        'status': activity.status,
        'estimated_minutes': activity.estimated_minutes,
        'actual_minutes': activity.actual_minutes,
        'start_time': _safe_time(activity.start_time),
        'end_time': _safe_time(activity.end_time),
        'order': activity.order,
        'day_entry_id': activity.day_entry_id,
        'goal_id': activity.goal_id,
        'category_id': activity.category_id,
        'created_at': activity.created_at.isoformat(),
        'updated_at': activity.updated_at.isoformat(),
    }


def _validate_positive_int(value, field_name):
    try:
        v = int(value)
        if v < 1:
            raise ValueError
        return v, None
    except (TypeError, ValueError):
        return None, JsonResponse({'error': f'{field_name} must be a positive integer'}, status=400)


def _resolve_activity_fks(data, user):
    fks = {}

    if 'day_entry_id' in data:
        if data['day_entry_id'] is None:
            fks['day_entry'] = None
        else:
            try:
                fks['day_entry'] = DayEntry.objects.get(id=data['day_entry_id'], user=user)
            except DayEntry.DoesNotExist:
                return None, JsonResponse({'error': 'DayEntry not found'}, status=404)

    if 'goal_id' in data:
        if data['goal_id'] is None:
            fks['goal'] = None
        else:
            try:
                fks['goal'] = Goal.objects.get(id=data['goal_id'], user=user)
            except Goal.DoesNotExist:
                return None, JsonResponse({'error': 'Goal not found'}, status=404)

    if 'category_id' in data:
        if data['category_id'] is None:
            fks['category'] = None
        else:
            try:
                fks['category'] = Category.objects.get(id=data['category_id'], user=user)
            except Category.DoesNotExist:
                return None, JsonResponse({'error': 'Category not found'}, status=404)

    return fks, None




class ActivitiesListView(AuthMixin, View):
    def get(self, request):
        qs = Activity.objects.filter(user=self.user).order_by('day_entry__date', 'order', 'created_at')

        day_entry_id = request.GET.get('day_entry_id')
        status = request.GET.get('status')
        activity_type = request.GET.get('activity_type')

        if day_entry_id:
            qs = qs.filter(day_entry_id=day_entry_id)
        if status:
            qs = qs.filter(status=status)
        if activity_type:
            qs = qs.filter(activity_type=activity_type)

        return JsonResponse({'activities': [_activity_json(a) for a in qs]})


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

        status_val = data.get('status', 'pending')
        if status_val not in _VALID_STATUSES:
            return JsonResponse({'error': f'Invalid status. Allowed: {sorted(_VALID_STATUSES)}'}, status=400)

        estimated_minutes = None
        if data.get('estimated_minutes') is not None:
            estimated_minutes, err = _validate_positive_int(data['estimated_minutes'], 'estimated_minutes')
            if err:
                return err

        actual_minutes = None
        if data.get('actual_minutes') is not None:
            actual_minutes, err = _validate_positive_int(data['actual_minutes'], 'actual_minutes')
            if err:
                return err
        elif status_val == 'completed' and estimated_minutes:
            actual_minutes = estimated_minutes

        fks, err = _resolve_activity_fks(data, self.user)
        if err:
            return err

        activity = Activity.objects.create(
            user=self.user,
            day_entry=fks.get('day_entry'),
            goal=fks.get('goal'),
            category=fks.get('category'),
            title=title,
            description=data.get('description') or None,
            activity_type=activity_type,
            status=status_val,
            estimated_minutes=estimated_minutes,
            actual_minutes=actual_minutes,
            start_time=data.get('start_time') or None,
            end_time=data.get('end_time') or None,
            order=data.get('order', 0),
        )
        activity = Activity.objects.get(id=activity.id)
        return JsonResponse(_activity_json(activity), status=201)



class ActivitiesDetailView(AuthMixin, View):
    def _get_activity(self, id):
        try:
            return Activity.objects.get(id=id, user=self.user), None
        except Activity.DoesNotExist:
            return None, JsonResponse({'error': 'Activity not found'}, status=404)

    def get(self, request, id):
        activity, err = self._get_activity(id)
        if err:
            return err
        return JsonResponse(_activity_json(activity))


    def put(self, request, id):
        activity, err = self._get_activity(id)
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
            activity.title = title

        if 'activity_type' in data:
            if data['activity_type'] not in _VALID_ACTIVITY_TYPES:
                return JsonResponse({'error': f'Invalid activity_type. Allowed: {sorted(_VALID_ACTIVITY_TYPES)}'}, status=400)
            activity.activity_type = data['activity_type']

        if 'status' in data:
            if data['status'] not in _VALID_STATUSES:
                return JsonResponse({'error': f'Invalid status. Allowed: {sorted(_VALID_STATUSES)}'}, status=400)
            activity.status = data['status']

        if 'estimated_minutes' in data:
            if data['estimated_minutes'] is None:
                activity.estimated_minutes = None
            else:
                v, err = _validate_positive_int(data['estimated_minutes'], 'estimated_minutes')
                if err:
                    return err
                activity.estimated_minutes = v

        if 'actual_minutes' in data:
            if data['actual_minutes'] is None:
                activity.actual_minutes = None
            else:
                v, err = _validate_positive_int(data['actual_minutes'], 'actual_minutes')
                if err:
                    return err
                activity.actual_minutes = v
        elif activity.status == 'completed' and activity.actual_minutes is None and activity.estimated_minutes:
            activity.actual_minutes = activity.estimated_minutes

        fks, err = _resolve_activity_fks(data, self.user)
        if err:
            return err
        for attr, val in fks.items():
            setattr(activity, attr, val)

        for field in ('description', 'start_time', 'end_time', 'order'):
            if field in data:
                setattr(activity, field, data[field] if data[field] != '' else None)

        activity.save()
        activity.refresh_from_db()
        return JsonResponse(_activity_json(activity))


    def delete(self, request, id):
        activity, err = self._get_activity(id)
        if err:
            return err
        activity.delete()
        return JsonResponse({'message': 'Activity deleted'})




class DayEntryActivitiesView(AuthMixin, View):
    def _get_entry(self, id):
        try:
            return DayEntry.objects.get(id=id, user=self.user), None
        except DayEntry.DoesNotExist:
            return None, JsonResponse({'error': 'DayEntry not found'}, status=404)

    def get(self, request, id):
        entry, err = self._get_entry(id)
        if err:
            return err
        qs = (Activity.objects
              .filter(user=self.user, day_entry=entry)
              .order_by('order', 'created_at'))
        return JsonResponse({'activities': [_activity_json(a) for a in qs]})


    def post(self, request, id):
        entry, err = self._get_entry(id)
        if err:
            return err

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

        status_val = data.get('status', 'pending')
        if status_val not in _VALID_STATUSES:
            return JsonResponse({'error': f'Invalid status. Allowed: {sorted(_VALID_STATUSES)}'}, status=400)

        estimated_minutes = None
        if data.get('estimated_minutes') is not None:
            estimated_minutes, err = _validate_positive_int(data['estimated_minutes'], 'estimated_minutes')
            if err:
                return err

        actual_minutes = None
        if data.get('actual_minutes') is not None:
            actual_minutes, err = _validate_positive_int(data['actual_minutes'], 'actual_minutes')
            if err:
                return err
        elif status_val == 'completed' and estimated_minutes:
            actual_minutes = estimated_minutes

        data_copy = {k: v for k, v in data.items() if k != 'day_entry_id'}
        fks, err = _resolve_activity_fks(data_copy, self.user)
        if err:
            return err

        activity = Activity.objects.create(
            user=self.user,
            day_entry=entry,
            goal=fks.get('goal'),
            category=fks.get('category'),
            title=title,
            description=data.get('description') or None,
            activity_type=activity_type,
            status=status_val,
            estimated_minutes=estimated_minutes,
            actual_minutes=actual_minutes,
            start_time=data.get('start_time') or None,
            end_time=data.get('end_time') or None,
            order=data.get('order', 0),
        )
        activity = Activity.objects.get(id=activity.id)
        return JsonResponse(_activity_json(activity), status=201)




def _day_entry_detail_json(entry, activities=None):
    data = _day_entry_json(entry)
    data['activities'] = [_activity_json(a) for a in (activities or [])]
    return data


class DayEntriesDetailView(AuthMixin, View):
    def get(self, request, id):
        try:
            entry = DayEntry.objects.select_related('main_goal').get(id=id, user=self.user)
        except DayEntry.DoesNotExist:
            return JsonResponse({'error': 'DayEntry not found'}, status=404)

        activities = (Activity.objects
                      .filter(user=self.user, day_entry=entry)
                      .select_related('category')
                      .order_by('order', 'created_at'))
        return JsonResponse(_day_entry_detail_json(entry, activities))






def _parse_date_param(request, param):
    raw = request.GET.get(param)
    if not raw:
        return None, None
    try:
        return dt.date.fromisoformat(raw), None
    except ValueError:
        return None, JsonResponse({'error': f'Invalid {param} date format. Use YYYY-MM-DD.'}, status=400)


def _default_range():
    today = dt.date.today()
    return today.replace(day=1), today


def _compute_streak(user, today):
    closed_dates = set(
        DayEntry.objects.filter(user=user, is_closed=True, status__in=['completed', 'partial'])
        .values_list('date', flat=True)
    )
    streak = 0
    day = today
    while day in closed_dates:
        streak += 1
        day -= dt.timedelta(days=1)
    return streak



class StatsSummaryView(AuthMixin, View):
    def get(self, request):
        date_from, err = _parse_date_param(request, 'from')
        if err:
            return err
        date_to, err = _parse_date_param(request, 'to')
        if err:
            return err
        if not date_from or not date_to:
            date_from, date_to = _default_range()

        entries = DayEntry.objects.filter(user=self.user, date__gte=date_from, date__lte=date_to)
        total_days = entries.count()
        completed = entries.filter(status='completed').count()
        partial = entries.filter(status='partial').count()
        failed = entries.filter(status='failed').count()
        closed = entries.filter(is_closed=True).count()

        activities = Activity.objects.filter(
            user=self.user,
            day_entry__date__gte=date_from,
            day_entry__date__lte=date_to,
        )
        total_activities = activities.count()
        completed_activities = activities.filter(status='completed').count()
        total_minutes = sum(v for v in activities.values_list('actual_minutes', flat=True) if v is not None)
        avg_progress = sum(e.progress_percent for e in entries) / total_days if total_days else 0
        streak = _compute_streak(self.user, dt.date.today())

        return JsonResponse({
            'period': {'from': date_from.isoformat(), 'to': date_to.isoformat()},
            'days': {
                'total': total_days,
                'completed': completed,
                'partial': partial,
                'failed': failed,
                'closed': closed,
                'avg_progress_percent': round(avg_progress, 1),
            },
            'activities': {
                'total': total_activities,
                'completed': completed_activities,
                'completion_rate': round(completed_activities / total_activities * 100, 1) if total_activities else 0,
                'total_minutes': total_minutes,
            },
            'streak': streak,
        })



class StatsCategoriesView(AuthMixin, View):
    def get(self, request):
        date_from, err = _parse_date_param(request, 'from')
        if err:
            return err
        date_to, err = _parse_date_param(request, 'to')
        if err:
            return err
        if not date_from or not date_to:
            date_from, date_to = _default_range()

        activities = Activity.objects.filter(
            user=self.user,
            day_entry__date__gte=date_from,
            day_entry__date__lte=date_to,
        ).select_related('category')

        cat_map = {}
        uncategorized = {'id': None, 'name': 'Uncategorized', 'total': 0, 'completed': 0, 'minutes': 0}
        for a in activities:
            if a.category_id is None:
                uncategorized['total'] += 1
                if a.status == 'completed':
                    uncategorized['completed'] += 1
                if a.actual_minutes:
                    uncategorized['minutes'] += a.actual_minutes
            else:
                key = a.category_id
                if key not in cat_map:
                    cat_map[key] = {
                        'id': a.category_id,
                        'name': a.category.name,
                        'icon': a.category.icon,
                        'color': a.category.color,
                        'total': 0,
                        'completed': 0,
                        'minutes': 0,
                    }
                cat_map[key]['total'] += 1
                if a.status == 'completed':
                    cat_map[key]['completed'] += 1
                if a.actual_minutes:
                    cat_map[key]['minutes'] += a.actual_minutes

        results = sorted(cat_map.values(), key=lambda x: x['minutes'], reverse=True)
        if uncategorized['total'] > 0:
            results.append(uncategorized)

        for r in results:
            r['completion_rate'] = round(r['completed'] / r['total'] * 100, 1) if r['total'] else 0

        return JsonResponse({
            'period': {'from': date_from.isoformat(), 'to': date_to.isoformat()},
            'categories': results,
        })



class StatsStreakView(AuthMixin, View):
    def get(self, request):
        today = dt.date.today()
        current_streak = _compute_streak(self.user, today)

        closed_dates = sorted(
            DayEntry.objects.filter(user=self.user, is_closed=True, status__in=['completed', 'partial'])
            .values_list('date', flat=True)
        )
        best_streak = 0
        run = 0
        prev = None
        for d in closed_dates:
            if prev is not None and (d - prev).days == 1:
                run += 1
            else:
                run = 1
            if run > best_streak:
                best_streak = run
            prev = d

        last_entry = DayEntry.objects.filter(user=self.user, is_closed=True).order_by('-date').first()

        return JsonResponse({
            'current_streak': current_streak,
            'best_streak': best_streak,
            'last_closed_date': last_entry.date.isoformat() if last_entry else None,
        })




class StatsWeeklyView(AuthMixin, View):
    def get(self, request):
        date_from, err = _parse_date_param(request, 'from')
        if err:
            return err
        date_to, err = _parse_date_param(request, 'to')
        if err:
            return err
        if not date_from or not date_to:
            date_to = dt.date.today()
            date_from = date_to - dt.timedelta(days=6)

        entries = {
            e.date: e
            for e in DayEntry.objects.filter(user=self.user, date__gte=date_from, date__lte=date_to)
        }
        activities_by_date = {}
        for a in Activity.objects.filter(
            user=self.user,
            day_entry__date__gte=date_from,
            day_entry__date__lte=date_to,
        ).select_related('day_entry'):
            d = a.day_entry.date
            activities_by_date.setdefault(d, []).append(a)

        days = []
        delta = date_to - date_from
        for i in range(delta.days + 1):
            d = date_from + dt.timedelta(days=i)
            entry = entries.get(d)
            day_acts = activities_by_date.get(d, [])
            completed_acts = sum(1 for a in day_acts if a.status == 'completed')
            minutes = sum(a.actual_minutes for a in day_acts if a.actual_minutes)
            days.append({
                'date': d.isoformat(),
                'status': entry.status if entry else None,
                'progress_percent': entry.progress_percent if entry else None,
                'is_closed': entry.is_closed if entry else False,
                'activities_total': len(day_acts),
                'activities_completed': completed_acts,
                'minutes': minutes,
            })

        return JsonResponse({
            'period': {'from': date_from.isoformat(), 'to': date_to.isoformat()},
            'days': days,
        })




class DashboardTodayView(AuthMixin, View):
    def get(self, request):
        today = dt.date.today()

        entry, _ = DayEntry.objects.get_or_create(user=self.user, date=today)
        entry = DayEntry.objects.select_related('main_goal').get(id=entry.id)

        activities = (Activity.objects
                      .filter(user=self.user, day_entry=entry)
                      .select_related('category')
                      .order_by('order', 'created_at'))

        try:
            profile = self.user.profile
            profile_data = {'name': profile.name, 'avatar_url': profile.avatar_url}
        except UserProfile.DoesNotExist:
            profile_data = {'name': self.user.username, 'avatar_url': None}

        streak = _compute_streak(self.user, today)
        month_start = today.replace(day=1)
        month_entries = DayEntry.objects.filter(user=self.user, date__gte=month_start, date__lte=today)
        month_completed = month_entries.filter(status='completed').count()
        month_total = month_entries.count()

        activities_today = [_activity_json(a) for a in activities]
        completed_today = sum(1 for a in activities_today if a['status'] == 'completed')

        return JsonResponse({
            'profile': profile_data,
            'today': _day_entry_json(entry),
            'activities_today': activities_today,
            'quick_stats': {
                'streak': streak,
                'month_days_completed': month_completed,
                'month_days_total': month_total,
                'activities_completed_today': completed_today,
                'activities_total_today': len(activities_today),
            },
        })





class CalendarView(AuthMixin, View):
    def get(self, request):
        today = dt.date.today()
        try:
            year = int(request.GET.get('year', today.year))
            month = int(request.GET.get('month', today.month))
            if not (1 <= month <= 12):
                raise ValueError
        except (TypeError, ValueError):
            return JsonResponse({'error': 'year and month must be valid integers (month 1–12)'}, status=400)

        import calendar as _cal
        _, days_in_month = _cal.monthrange(year, month)
        date_from = dt.date(year, month, 1)
        date_to = dt.date(year, month, days_in_month)

        entries = {
            e.date: e
            for e in DayEntry.objects.filter(user=self.user, date__gte=date_from, date__lte=date_to)
                             .select_related('main_goal')
        }

        days = []
        for day_num in range(1, days_in_month + 1):
            d = dt.date(year, month, day_num)
            entry = entries.get(d)
            days.append({
                'date': d.isoformat(),
                'has_entry': entry is not None,
                'status': entry.status if entry else None,
                'progress_percent': entry.progress_percent if entry else None,
                'is_closed': entry.is_closed if entry else False,
                'main_goal_title': entry.main_goal.title if (entry and entry.main_goal) else None,
            })

        return JsonResponse({
            'year': year,
            'month': month,
            'days': days,
        })

class DayEntriesReopenView(AuthMixin, View):
    def put(self, request, id):
        try:
            entry = DayEntry.objects.select_related('main_goal').get(id=id, user=self.user)
        except DayEntry.DoesNotExist:
            return JsonResponse({'error': 'DayEntry not found'}, status=404)

        if not entry.is_closed:
            return JsonResponse({'error': 'DayEntry is not closed'}, status=409)

        entry.is_closed = False
        entry.closed_at = None
        entry.status = 'open'
        entry.save()
        return JsonResponse(_day_entry_json(entry))